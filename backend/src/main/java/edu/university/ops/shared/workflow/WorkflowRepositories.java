package edu.university.ops.shared.workflow;

import edu.university.ops.shared.workflow.WorkflowEnums.TaskStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Spring Data repositories of the workflow engine; package-private, only the engine uses them.

interface WorkflowDefinitionRepository extends Repository<WorkflowDefinition, UUID> {
    Optional<WorkflowDefinition> findFirstByCodeAndActiveTrueOrderByVersionDesc(String code);

    Optional<WorkflowDefinition> findById(UUID id);
}

interface WorkflowInstanceRepository extends Repository<WorkflowInstance, UUID> {
    WorkflowInstance save(WorkflowInstance instance);

    Optional<WorkflowInstance> findById(UUID id);

    List<WorkflowInstance> findByBusinessObjectTypeAndBusinessObjectIdOrderByCreatedAt(String type, UUID id);
}

interface UserTaskRepository extends Repository<UserTask, UUID> {
    UserTask save(UserTask task);

    Optional<UserTask> findById(UUID id);

    @Query("select t from UserTask t where t.status = :status and t.step.instance.id = :instanceId")
    List<UserTask> findByInstance(@Param("instanceId") UUID instanceId, @Param("status") TaskStatus status);

    @Query("""
            select t from UserTask t
            where t.status = 'OPEN'
              and (t.step.assignedEmployeeId in :employeeIds or t.step.assignedRole in :roles)
            order by t.createdAt""")
    List<UserTask> findOpenFor(@Param("employeeIds") Collection<UUID> employeeIds,
                               @Param("roles") Collection<String> roles);

    @Query("select t from UserTask t where t.status = 'OPEN' and t.createdAt < :before order by t.createdAt")
    List<UserTask> findOpenCreatedBefore(@Param("before") Instant before);

    @Query("select count(t) from UserTask t where t.status = 'OPEN'")
    long countOpen();
}

interface DelegationRepository extends Repository<Delegation, UUID> {
    Delegation save(Delegation delegation);

    Optional<Delegation> findById(UUID id);

    List<Delegation> findByDelegateIdAndActiveTrue(UUID delegateId);

    List<Delegation> findByDelegatorIdAndActiveTrue(UUID delegatorId);

    List<Delegation> findByDelegatorIdOrDelegateIdOrderByValidFromDesc(UUID delegatorId, UUID delegateId);
}
