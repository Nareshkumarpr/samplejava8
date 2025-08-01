# samplejava8


Yes, it’s possible to integrate the draft functionality into the pending_approvals table instead of maintaining a separate drafts table. This approach reduces the number of tables, simplifies the data model, and consolidates the logic for managing drafts and approvals in one place. However, it requires careful design to maintain the separation between draft (maker-only, pre-submission) and submitted (pending approval) states while ensuring the existing requirements (maker-checker process, start/end dates, history tracking, and draft privacy) are met.
Below is the updated system design, merging drafts into the pending_approvals table and addressing the implications of this change.

Updated Inventory Management System Design with Consolidated Drafts and Approvals
1. System Overview
* Objective: Merge draft functionality into the pending_approvals table while preserving:
    * “Save before submit” functionality (drafts private to makers).
    * Maker-checker approval process.
    * Start and end date availability for approved items.
    * History tracking for approved changes.
    * Return old values for pending updates until approved.
* Key Change: The pending_approvals table now handles both drafts (pre-submission) and submitted approvals using a status field to differentiate states.
2. Updated Data Model
2.1 Core Tables
The drafts table is removed, and pending_approvals is modified to accommodate drafts.
Inventory Table (Unchanged)
inventory (
  item_id: UUID (PK),
  name: VARCHAR(100),
  description: TEXT,
  quantity: INTEGER,
  unit_price: DECIMAL(10,2),
  category: VARCHAR(50),
  status: ENUM('ACTIVE', 'INACTIVE'),
  start_date: DATE,
  end_date: DATE,
  last_updated: TIMESTAMP,
  version: INTEGER
)
Pending Approvals Table (Updated to Include Drafts)
pending_approvals (
  approval_id: UUID (PK),
  item_id: UUID (FK to inventory, nullable for CREATE operations),
  operation: ENUM('CREATE', 'UPDATE', 'DELETE'),
  proposed_changes: JSONB,          -- Stores all fields (incl. start_date, end_date)
  maker_id: UUID (FK to users),
  checker_id: UUID (FK to users, nullable),
  status: ENUM('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'), -- Added DRAFT status
  created_at: TIMESTAMP,
  updated_at: TIMESTAMP
)
History Table (Unchanged)
inventory_history (
  history_id: UUID (PK),
  item_id: UUID (FK to inventory),
  operation: ENUM('CREATE', 'UPDATE', 'DELETE'),
  old_values: JSONB,
  new_values: JSONB,
  changed_by: UUID (FK to users),
  change_timestamp: TIMESTAMP,
  approval_id: UUID (FK to pending_approvals, nullable)
)
Users Table (Unchanged)
users (
  user_id: UUID (PK),
  username: VARCHAR(50),
  role: ENUM('MAKER', 'CHECKER', 'ADMIN'),
  email: VARCHAR(100),
  created_at: TIMESTAMP
)
2.2 Key Changes
* Status Field: The status field in pending_approvals now includes DRAFT to represent pre-submission changes, alongside PENDING, APPROVED, and REJECTED.
* Draft Privacy: Records with status = 'DRAFT' are only visible to the maker_id associated with the record.
* No Separate Draft Table: All draft and approval-related data is stored in pending_approvals, reducing schema complexity.
3. Updated API Design
3.1 New/Updated Endpoints
Draft Management (Now Handled via Pending Approvals)
* POST /api/pending-approvals/draft - Save a draft (create/update/delete, maker only)
* PUT /api/pending-approvals/draft/{approval_id} - Update an existing draft (maker only)
* GET /api/pending-approvals/drafts - List drafts for the authenticated maker
* GET /api/pending-approvals/draft/{approval_id} - Get specific draft (maker only)
* POST /api/pending-approvals/draft/{approval_id}/submit - Submit draft for approval (maker only)
* DELETE /api/pending-approvals/draft/{approval_id} - Delete a draft (maker only)
Inventory Management (Unchanged)
* POST /api/inventory - Creates a draft (status = ‘DRAFT’) in pending_approvals
* PUT /api/inventory/{item_id} - Creates/updates a draft for update
* DELETE /api/inventory/{item_id} - Creates a draft for deletion
* GET /api/inventory/{item_id} - Returns approved values (ignores drafts and pending)
* GET /api/inventory - Lists approved items within start_date and end_date
Approval Management (Unchanged)
* GET /api/approvals/pending - List pending approvals (checker, only status = 'PENDING')
* POST /api/approvals/{approval_id}/approve - Approve changes (checker)
* POST /api/approvals/{approval_id}/reject - Reject changes (checker)
History (Unchanged)
* GET /api/inventory/{item_id}/history - Get change history
4. System Architecture Updates
4.1 Key Changes
* Consolidated Table: pending_approvals now stores both drafts (status = 'DRAFT') and approval requests (status = 'PENDING', 'APPROVED', 'REJECTED').
* Draft Visibility: Queries for drafts filter by status = 'DRAFT' and maker_id to ensure privacy.
* Submission: Submitting a draft updates the status from DRAFT to PENDING in pending_approvals.
* No Impact on Inventory: Records with status = 'DRAFT' or status = 'PENDING' do not affect inventory until approved.
4.2 Updated Workflow
Save Draft
1. Maker creates/updates a draft (create/update/delete).
2. System:
    * Validates input (e.g., start_date <= end_date).
    * Saves to pending_approvals with status = 'DRAFT' and maker_id.
    * Record is only visible to the maker via GET /api/pending-approvals/drafts.
3. No impact on inventory.
Submit Draft
1. Maker submits a draft via POST /api/pending-approvals/draft/{approval_id}/submit.
2. System:
    * Validates the record (status = 'DRAFT', correct maker_id).
    * Updates status to PENDING in pending_approvals.
    * Record is now visible to checkers via GET /api/approvals/pending.
3. No changes to inventory until approved.
Create Operation
1. Maker saves draft (status = 'DRAFT') for new item in pending_approvals.
2. On submit: Updates status to PENDING.
3. Checker approves:
    * Inserts into inventory.
    * Creates history entry in inventory_history.
    * Updates pending_approvals to status = 'APPROVED'.
    * Publishes Kafka audit message.
4. Checker rejects: Updates pending_approvals to status = 'REJECTED'.
Update Operation
1. Maker saves draft (status = 'DRAFT') for update in pending_approvals.
2. On submit: Updates status to PENDING.
3. Checker approves:
    * Updates inventory with proposed_changes.
    * Creates history entry.
    * Updates pending_approvals to status = 'APPROVED'.
    * Publishes Kafka message.
4. Checker rejects: Updates pending_approvals to status = 'REJECTED'.
5. Queries return old inventory values while status = 'PENDING'.
Delete Operation
1. Maker saves draft (status = 'DRAFT') for deletion in pending_approvals.
2. On submit: Updates status to PENDING.
3. Checker approves:
    * Soft deletes in inventory (status = 'INACTIVE').
    * Creates history entry.
    * Updates pending_approvals to status = 'APPROVED'.
4. Item remains available until approved.
Read Operation
* Queries inventory for approved items where:
    * status = 'ACTIVE'
    * CURRENT_DATE >= start_date AND CURRENT_DATE <= end_date
* Ignores pending_approvals records (both DRAFT and PENDING).
5. Implementation Details
5.1 Approval Service (Updated to Handle Drafts)
public class ApprovalService {
    @Transactional
    public Approval saveDraft(ItemDraftDTO draftDTO, User maker) {
        validateMakerRole(maker);
        validateDates(draftDTO.getStartDate(), draftDTO.getEndDate());

        Approval draft = new Approval();
        draft.setOperation(draftDTO.getOperation());
        draft.setItemId(draftDTO.getItemId()); // Null for CREATE
        draft.setProposedChanges(toJson(draftDTO));
        draft.setMakerId(maker.getId());
        draft.setStatus(ApprovalStatus.DRAFT);
        return approvalRepo.save(draft);
    }

    @Transactional
    public Approval submitDraft(UUID approvalId, User maker) {
        validateMakerRole(maker);
        Approval draft = approvalRepo.findByIdAndMakerIdAndStatus(approvalId, maker.getId(), ApprovalStatus.DRAFT)
            .orElseThrow(() -> new NotFoundException("Draft not found or not in DRAFT status"));

        draft.setStatus(ApprovalStatus.PENDING);
        return approvalRepo.save(draft);
    }

    @Transactional
    public void approve(Approval approval, User checker) {
        validateCheckerRole(checker);
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new InvalidStateException("Approval not in PENDING status");
        }

        if (approval.getOperation() == Operation.CREATE) {
            Item item = fromJson(approval.getProposedChanges(), Item.class);
            inventoryRepo.save(item);
        } else if (approval.getOperation() == Operation.UPDATE) {
            Item item = inventoryRepo.findById(approval.getItemId());
            applyChanges(item, approval.getProposedChanges());
            inventoryRepo.save(item);
        } else if (approval.getOperation() == Operation.DELETE) {
            Item item = inventoryRepo.findById(approval.getItemId());
            item.setStatus(Status.INACTIVE);
            inventoryRepo.save(item);
        }

        historyRepo.save(createHistoryEntry(approval));
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setCheckerId(checker.getId());
        kafkaTemplate.send("audit-topic", createAuditMessage(approval));
    }

    public List getMakerDrafts(User maker) {
        return approvalRepo.findByMakerIdAndStatus(maker.getId(), ApprovalStatus.DRAFT);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("start_date must be before or equal to end_date");
        }
    }
}
5.2 Approval Repository
@Repository
public interface ApprovalRepository extends JpaRepository {
    List findByMakerIdAndStatus(UUID makerId, ApprovalStatus status);
    Optional findByIdAndMakerIdAndStatus(UUID id, UUID makerId, ApprovalStatus status);
    List findByStatus(ApprovalStatus status); // For checkers to get PENDING
}
5.3 Inventory Repository (Unchanged)
@Repository
public interface InventoryRepository extends JpaRepository {
    @Query("SELECT i FROM Item i WHERE i.itemId = :id AND i.status = 'ACTIVE' " +
           "AND CURRENT_DATE >= i.startDate AND CURRENT_DATE <= i.endDate")
    Optional findActiveById(@Param("id") UUID id);

    @Query("SELECT i FROM Item i WHERE i.status = 'ACTIVE' " +
           "AND CURRENT_DATE >= i.startDate AND CURRENT_DATE <= i.endDate")
    Page findAllActive(Pageable pageable);
}
5.4 History Service (Unchanged)
public class HistoryService {
    public History createHistoryEntry(Approval approval) {
        History history = new History();
        history.setItemId(approval.getItemId());
        history.setOperation(approval.getOperation());
        if (approval.getOperation() != Operation.CREATE) {
            Item current = inventoryRepo.findById(approval.getItemId());
            history.setOldValues(toJson(current));
        }
        history.setNewValues(approval.getProposedChanges());
        history.setChangedBy(approval.getMakerId());
        history.setApprovalId(approval.getId());
        return historyRepo.save(history);
    }
}
6. Database Optimization
CREATE INDEX idx_inventory_status_dates ON inventory(status, start_date, end_date);
CREATE INDEX idx_pending_approvals_maker_status ON pending_approvals(maker_id, status);
CREATE INDEX idx_history_item_id ON inventory_history(item_id, change_timestamp);
7. Trade-offs of Merging Drafts into Pending Approvals
Advantages
* Simpler Schema: Eliminates the need for a separate drafts table, reducing database complexity.
* Unified Logic: Draft and approval management share the same table and service, simplifying code.
* Fewer Joins: No need to join drafts and pending_approvals for tracking submitted drafts.
Disadvantages
* Mixed States: Combining drafts and approvals in one table may increase complexity in queries and business logic to differentiate DRAFT vs. PENDING/APPROVED/REJECTED.
* Scalability: If drafts are frequently created and deleted without submission, the pending_approvals table could grow unnecessarily (mitigated by periodic cleanup of old drafts).
* Status Management: Additional care needed to ensure DRAFT records are not accidentally processed as approvals.
8. Additional Notes
* Draft Privacy: Ensured by filtering status = 'DRAFT' and maker_id in queries (e.g., findByMakerIdAndStatus).
* Submission: Transition from DRAFT to PENDING is a simple status update, maintaining data integrity.
* No Inventory Impact: DRAFT and PENDING records do not affect inventory until APPROVED.
* History: Only APPROVED changes are logged in inventory_history.
* Cleanup: Consider a scheduled job to remove old DRAFT records to prevent table bloat.
9. Security Considerations
* Access Control: DRAFT records are restricted to the maker_id via repository queries.
* Immutable PENDING Records: Once status = 'PENDING', records cannot be modified (except by checkers approving/rejecting).
* Audit Logging: Draft creation and submission are logged via Kafka, but only approved changes are stored in inventory_history.
This design successfully integrates drafts into the pending_approvals table while maintaining all existing functionality. Let me know if you need further refinements or additional features!
