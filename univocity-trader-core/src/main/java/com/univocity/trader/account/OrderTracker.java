package com.univocity.trader.account;

import com.univocity.trader.utils.ThreadName;
import org.slf4j.*;

import java.util.*;

import static com.univocity.trader.account.Order.Side.*;
import static com.univocity.trader.account.Order.Status.*;
import static com.univocity.trader.account.TradingManager.*;

public final class OrderTracker {

	private static final Logger log = LoggerFactory.getLogger(OrderTracker.class);

	private final OrderSet pendingOrders = new OrderSet();
	private final OrderSet finalizedOrders = new OrderSet();
	private final TradingManager tradingManager;
	private final AccountManager account;
	private final OrderManager orderManager;
	private final Trader trader;

	OrderTracker(TradingManager tradingManager) {
		this.tradingManager = tradingManager;
		this.account = tradingManager.getAccount();
		this.orderManager = tradingManager.orderManager;
		this.trader = tradingManager.trader;
	}


	public void waitForFill(Order order) {
		if (order.isFinalized()) {
			return;
		}
		synchronized (pendingOrders) {
			pendingOrders.addOrReplace(order);
		}
		if (account.isSimulated()) {
			return;
		}
		Thread thread = new Thread(() -> {
			Order o = order;
			Order updated;
			while (true) {
				try {
					try {
						Thread.sleep(orderManager.getOrderUpdateFrequency().ms);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}

					updated = account.updateOrderStatus(o);
					processOrderUpdate(o, updated);
					if (o.isFinalized()) {
						return;
					}
					o = updated;
				} catch (Exception e) {
					log.error("Error tracking state of order " + o, e);
					return;
				}
			}
		});
		thread.setName(ThreadName.generateNewName() + "-Order " + order.getOrderId() + " monitor: " + order.getSide() + " " + order.getSymbol());
		thread.start();
	}

	public boolean waitingForFill(String assetSymbol, Order.Side side, Trade.Side tradeSide) {
		synchronized (pendingOrders) {
			for (int i = pendingOrders.i - 1; i >= 0; i--) {
				Order order = pendingOrders.elements[i];

				if (order.isFinalized() || order.getTradeSide() != tradeSide) {
					continue;
				}

				if (order.getSide() == side && order.getAssetsSymbol().equals(assetSymbol)) {
					return true;
				}
				// If we want to know if there is an open order to buy BTC,
				// and the symbol is "ADABTC", we need to invert the side as
				// we are selling ADA to buy BTC.
				if (side == BUY && order.isSell() && order.getFundsSymbol().equals(assetSymbol)) {
					return true;

				} else if (side == SELL && order.isBuy() && order.getFundsSymbol().equals(assetSymbol)) {
					return true;
				}
			}
		}
		return false;
	}

	private void processAttached(Order order) {
		List<Order> attachments;

		if (order.getParent() != null) {
			attachments = order.getParent().getAttachments();
		} else {
			attachments = order.getAttachments();
		}

		if (attachments != null && order.isFinalized() && !account.isSimulated()){
			trader.processOrder(account.executeOrder(order));
		}
	}

	private void orderFinalized(Order order) {
		synchronized (pendingOrders) {
			pendingOrders.remove(order);
			if (order.getExecutedQuantity() != 0) {
				synchronized (finalizedOrders) {
					finalizedOrders.addOrReplace(order);
				}
			}
		}

		try {
			account.executeUpdateBalances();
		} finally {
			notifyFinalized(order, trader);
			List<Order> attachments;
			if (order.getParent() != null) {
				attachments = order.getParent().getAttachments();
			} else {
				attachments = order.getAttachments();
			}
			if (attachments != null && order.isCancelled() && order.getExecutedQuantity() == 0.0) {
				for (Order attached : attachments) {
					if (attached != order) {
						attached.cancel();
						notifyFinalized(attached, trader);
					}
				}
			}
		}

	}

	private void notifyFinalized(Order order, Trader trader) {
		try {
			orderManager.finalized(order, trader);
		} finally {
			tradingManager.notifyOrderFinalized(order);
		}
	}

	void initiateOrderMonitoring(Order order) {
		if (order != null) {
			if (order.getTrade() == null) {
				throw new IllegalStateException("Order " + order + " does not have a valid trade associated with it.");
			}
			switch (order.getStatus()) {
				case NEW:
				case PARTIALLY_FILLED:
					logOrderStatus("Tracking pending order. ", order);
					waitForFill(order);
					break;
				case FILLED:
					logOrderStatus("Completed order. ", order);
					orderFinalized(order);
					processAttached(order);
					break;
				case CANCELLED:
					logOrderStatus("Could not create order. ", order);
					orderFinalized(order);
					break;
			}
		}
	}

	public void updateOpenOrders() {
		synchronized (pendingOrders) {
			if (account.isSimulated() && !pendingOrders.isEmpty()) {
				Order[] pending = pendingOrders.elements.length > 1 ? pendingOrders.elements.clone() : pendingOrders.elements;
				final int start = pendingOrders.i;
				for (int i = start - 1; i >= 0; i--) {
					Order order = pending[i];
					Order update = account.updateOrderStatus(order);
					processOrderUpdate(order, update);
				}
			}
		}
	}

	private void processOrderUpdate(Order order, Order update) {
		if (update.isFinalized()) {
			logOrderStatus("Order finalized. ", update);
			orderFinalized(update);
			processAttached(update);
			return;
		} else {
			// update order status
			synchronized (pendingOrders) {
				pendingOrders.addOrReplace(update);
			}
		}

		if ((account.isSimulated() && update.hasPartialFillDetails()) || update.getExecutedQuantity() != order.getExecutedQuantity()) {
			logOrderStatus("Order updated. ", update);
			account.executeUpdateBalances();
			orderManager.updated(update, trader, tradingManager::resubmit);
		} else {
			logOrderStatus("Unchanged ", update);
			orderManager.unchanged(update, trader, tradingManager::resubmit);
		}

		//order manager could have cancelled the order
		if (update.getStatus() == CANCELLED) {
			cancelOrder(update, true);
		}
	}

	void cancelOrder(Order order) {
		cancelOrder(order, false);
	}

	void cancelOrder(Order order, boolean tryForceAccountCancellation) {
		Order update;
		synchronized (pendingOrders) {
			update = pendingOrders.get(order);
		}

		if (!tryForceAccountCancellation && (update == null || update.isFinalized())) {
			return;
		}
		if (update != null) {
			order = update;
		}

		try {
			order.cancel();
			account.cancel(order);
		} catch (Exception e) {
			log.error("Failed to execute cancellation of order '" + order + "' on exchange", e);
		} finally {
			orderFinalized(order);
			logOrderStatus("Cancellation via order manager: ", order);
		}
	}

	public void cancelStaleOrdersFor(Trade.Side side, Trader trader) {
		account.forEachTradingManager(tradingManager -> {
			if (!tradingManager.symbol.equals(this.tradingManager.symbol)) {
				tradingManager.orderTracker.executeCancelStaleOrdersFor(side, trader);
			}
		});
	}

	void executeCancelStaleOrdersFor(Trade.Side side, Trader trader) {
		List<Order> ordersToCancel = new ArrayList<>(1);
		synchronized (pendingOrders) {
			for (int i = pendingOrders.i - 1; i >= 0; i--) {
				Order order = pendingOrders.elements[i];
				if (order.isFinalized()) {
					continue;
				}
				if (tradingManager.orderManager.cancelToReleaseFundsFor(order, tradingManager.trader, trader)) {
					ordersToCancel.add(order);
				}
			}
		}
		ordersToCancel.forEach(this::cancelOrder);
	}

	public void cancelAllOrders() {
		synchronized (pendingOrders) {
			for (int i = pendingOrders.i - 1; i >= 0; i--) {
				Order order = pendingOrders.elements[i];
				if (order != null) {
					order.cancel();
					processOrderUpdate(order, order);
				}
			}
		}
	}

	public void clear() {
		synchronized (pendingOrders) {
			pendingOrders.clear();
		}
	}

	public Order getOrder(Order order) {
		Order latestUpdate;
		synchronized (pendingOrders) {
			latestUpdate = pendingOrders.get(order);
			if (latestUpdate != null && latestUpdate.isFinalized()) {
				synchronized (finalizedOrders) {
					finalizedOrders.addOrReplace(order);
					pendingOrders.remove(order);
				}
			}
		}

		if (latestUpdate == null) {
			synchronized (finalizedOrders) {
				latestUpdate = finalizedOrders.get(order);
				if (latestUpdate != null) {
					finalizedOrders.remove(order);
				}
			}
		}

		return latestUpdate == null ? order : latestUpdate;
	}
}
