package com.autonomousapi.core.workorder;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderItemRepository extends JpaRepository<WorkOrderItem, UUID> {

    List<WorkOrderItem> findAllByWorkOrderId(UUID workOrderId);

    List<WorkOrderItem> findAllByWorkOrderIdIn(List<UUID> workOrderIds);

    void deleteAllByWorkOrderId(UUID workOrderId);
}
