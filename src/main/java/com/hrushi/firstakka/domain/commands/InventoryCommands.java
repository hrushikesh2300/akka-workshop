package com.hrushi.firstakka.domain.commands;

import com.hrushi.firstakka.domain.model.OrderState.OrderLine;
import java.util.List;

public final class InventoryCommands {

    public record ReserveInventory(String orderId, List<OrderLine> lines) {}
    public record ReleaseInventory(String orderId) {}
}
