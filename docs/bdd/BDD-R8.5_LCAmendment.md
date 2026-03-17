---
Document ID: BDD-R8.5
Version: 0.1
System: Trade Finance
Module: Letter of Credit (LC)
BRD ID: BRD-002
Status: DRAFT
Last Updated: 2026-03-16
Author: [AI Agent]
---

# BDD Scenarios: LC Amendment (R8.5)

This document contains BDD scenarios for the LC Amendment process (R8.5).
The workflow covers the end-to-end process from amendment request creation to beneficiary response and system update.

---

## R8.5-UC1: Create Amendment Request

### Feature: Create Amendment Request

**As a** CSR (Customer Service Representative)
**I want to** create an amendment request for an existing issued LC
**So that** I can initiate the process to modify LC terms after issuance

**Background**
The CSR is logged into the Trade Finance system and has navigated to an existing issued LC record.

#### 1. Scenario: Successful creation of an Amendment Request (Happy Path)
**Given** the CSR is viewing an issued LC with LC Number "LC-TEST-001"
**And** the LC status is "Issued"
**When** the CSR clicks the **"Request Amendment"** button
**And** the CSR enters valid amendment details (e.g., increase amount by 20%, extend expiry date by 30 days)
**And** the CSR clicks the **"Create Amendment"** button
**Then** the system should validate that the LC is in a valid state for amendment
**And** the system should create an LcAmendment record linked to the LC
**And** the system should set the amendment status to "Draft"
**And** the system should display a success message: "Amendment request [AMENDMENT_ID] has been created successfully."
**And** the system should redirect the CSR to the **Amendment Detail** screen for this newly created record.

#### 2. Scenario: Creation fails due to LC not being in Issued status (Edge Case - Validation)
**Given** the CSR is viewing an LC with LC Number "LC-TEST-002"
**And** the LC status is "Draft" (not issued)
**When** the CSR clicks the **"Request Amendment"** button
**Then** the system should display an error message: "Amendments can only be requested for LCs in Issued status."
**And** the system should NOT create an amendment request.

#### 3. Scenario: UI prevents amendment of immutable fields (Edge Case - Data Integrity)
**Given** the CSR is creating an amendment for LC "LC-TEST-001"
**When** the CSR attempts to modify the LC Number field
**Then** the system should display a validation warning: "LC Number cannot be amended."
**And** the system should prevent the record from being saved if the CSR proceeds to click **"Create Amendment"**
**And** the system should display an error message: "Immutable fields cannot be modified via amendment."

### Business Rules & Validation
1. **Valid LC Status:** Amendment requests can only be created for LCs with status "Issued".
2. **Immutable Fields:** The following fields cannot be amended: LC Number, Applicant, Currency, Issuing Bank, Advising Bank.
3. **State Transition:** Upon successful creation, the `LcAmendment.amendmentStatusId` must be set to `LcTxDraft`.
4. **Linkage:** The amendment record must be properly linked to the parent LC via `lcId`.

---

## R8.5-UC2: Submit Amendment for Approval

### Feature: Submit Amendment for Approval

**As a** CSR (Customer Service Representative)
**I want to** submit the amendment request for approval through the proper channels
**So that** the amendment can be reviewed by the appropriate authorities

**Background**
The CSR has created an amendment draft and is on the Amendment Detail screen.

#### 1. Scenario: Successfully submit amendment to Supervisor (Happy Path)
**Given** the CSR is on the Amendment Detail screen for a Draft amendment
**And** the amendment has valid changes to amendable fields
**When** the CSR clicks the **"Submit for Review"** button
**Then** the system should validate that all required fields are completed
**And** the system should create a workflow task for the Branch Supervisor
**And** the system should change the amendment status to **"Submitted"**
**And** the system should notify the Branch Supervisor
**And** the system should display a success message: "Amendment request submitted for review."

#### 2. Scenario: Submit fails due to incomplete data (Edge Case - Validation)
**Given** the CSR is on the Amendment Detail screen for a Draft amendment
**When** the CSR leaves required amendment details incomplete (e.g., no changes specified)
**And** the CSR clicks the **"Submit for Review"** button
**Then** the system should display an error: "Please specify the changes to be made in the amendment."
**And** the system should highlight the incomplete sections
**And** the system should NOT submit the amendment for review.

#### 3. Scenario: Supervisor approves and forwards to IPC (Edge Case - Workflow)
**Given** the Branch Supervisor has reviewed the amendment request
**And** all validation checks pass
**When** the Supervisor clicks **"Approve and Forward to IPC"**
**Then** the system should create a task for the IPC Officer
**And** the system should change the amendment status to **"Pending IPC Approval"**
**And** the system should notify the IPC Officer
**And** the system should notify the CSR of the supervisor's approval.

#### 4. Scenario: Supervisor rejects amendment (Edge Case - Workflow)
**Given** the Branch Supervisor has reviewed the amendment request
**When** the Supervisor clicks **"Reject"** and provides a rejection reason
**Then** the system should change the amendment status to **"Rejected"**
**And** the system should send notification to CSR with the rejection reason
**And** the system should allow CSR to resubmit after corrections
**And** the system should preserve the original LC terms (no changes applied).

### Business Rules & Validation
1. **Approval Levels:** CSR → Supervisor → IPC (3-level approval for amendments).
2. **Rejection Reason:** Mandatory when rejecting an amendment request.
3. **Workflow Transition:** Each approval level must follow the defined routing sequence.
4. **Notification:** Appropriate notifications must be sent at each workflow transition.

---

## R8.5-UC3: IPC Approval and SWIFT Generation

### Feature: IPC Approval and SWIFT MT707 Generation

**As an** IPC (International Processing Center) Officer
**I want to** provide final approval for amendment requests and generate SWIFT MT707
**So that** the amendment can be officially processed and communicated to the beneficiary

**Background**
The amendment request has been approved by the Branch Supervisor and is pending IPC approval.

#### 1. Scenario: Successfully approve amendment and generate MT707 (Happy Path)
**Given** the IPC Officer is viewing a Pending IPC Approval amendment
**And** the amendment has valid changes that comply with UCP 600
**When** the IPC Officer clicks the **"Approve Amendment"** button
**Then** the system should validate beneficiary acceptance window constraints
**And** the system should generate a SWIFT MT707 message per standards
**And** the system should attach the MT707 message to the LC record
**And** the system should change the amendment status to **"Approved"**
**And** the system should notify the Advising Bank of the amendment
**And** the system should display a success message: "Amendment approved and MT707 generated."

#### 2. Scenario: IPC rejects amendment (Edge Case - Workflow)
**Given** the IPC Officer is viewing a Pending IPC Approval amendment
**When** the IPC Officer clicks **"Reject"** and provides a rejection reason
**Then** the system should change the amendment status to **"Rejected"**
**And** the system should send notification to Branch Supervisor with the rejection reason
**And** the system should notify the CSR
**And** the system should preserve the original LC terms (no changes applied).

#### 3. Scenario: System failure during MT707 generation (Edge Case - Integration)
**Given** the IPC Officer initiates amendment approval
**When** the SWIFT message generation service encounters an error
**Then** the system should display an error: "Unable to generate SWIFT MT707 message. Please try again later."
**And** the system should NOT change the amendment status
**And** the system should log the error for troubleshooting
**And** the system should rollback any partial changes made during the approval process.

### Business Rules & Validation
1. **SWIFT Compliance:** Generated MT707 messages must comply with SWIFT standards.
2. **Beneficiary Notification:** Advising Bank must be notified upon IPC approval.
3. **Error Handling:** System failures during approval must trigger appropriate rollback mechanisms.
4. **Audit Trail:** All approval/rejection actions must be tracked in the amendment history.

---

## R8.5-UC4: Beneficiary Response Processing

### Feature: Beneficiary Response Processing

**As a** System (Automated Process)
**I want to** process beneficiary responses to amendment requests
**So that** the LC terms are updated only upon beneficiary acceptance per UCP 600

**Background**
The Advising Bank has notified the beneficiary of the amendment via SWIFT MT707.

#### 1. Scenario: Beneficiary accepts amendment (Happy Path)
**Given** the beneficiary has received the SWIFT MT707 for amendment
**When** the beneficiary sends an acceptance response to the Advising Bank
**And** the Advising Bank forwards the acceptance to the Issuing Bank
**When** the system receives the beneficiary acceptance notification
**Then** the system should update the amendment confirmation status to **"Accepted"**
**And** the system should apply the shadow changes to the master LC record
**And** the system should increment the LC amendment number by 1
**And** the system should transition the LC status to **"Amended"**
**And** the system should update financials (charges, provisions) if applicable
**And** the system should notify all stakeholders (Applicant, CSR, Supervisor, IPC)
**And** the system should display a success message: "Amendment accepted by beneficiary and applied to LC."

#### 2. Scenario: Beneficiary rejects amendment (Edge Case - Business Rule)
**Given** the beneficiary has received the SWIFT MT707 for amendment
**When** the beneficiary sends a rejection response to the Advising Bank
**And** the Advising Bank forwards the rejection to the Issuing Bank
**When** the system receives the beneficiary rejection notification
**Then** the system should update the amendment confirmation status to **"Rejected"**
**And** the system should preserve the original LC terms (no changes applied)
**And** the system should notify all stakeholders of the beneficiary rejection
**And** the system should display an info message: "Amendment rejected by beneficiary. Original LC terms remain in effect."

#### 3. Scenario: Beneficiary acceptance window expired (Edge Case - Time Constraint)
**Given** the amendment has been approved by IPC
**And** the beneficiary acceptance window (per UCP 600 Article 10) has expired
**When** the system checks for beneficiary response
**Then** the system should treat lack of response as rejection after the window expires
**And** the system should update the amendment confirmation status to **"Expired"**
**And** the system should preserve the original LC terms (no changes applied)
**And** the system should notify all stakeholders of the expired acceptance window
**And** the system should display an info message: "Beneficiary acceptance window expired. Original LC terms remain in effect."

### Business Rules & Validation
1. **UCP 600 Article 10:** Beneficiary has the right to accept or reject amendments.
2. **Silence as Acceptance:** Depending on specific UCP interpretation, silence may constitute acceptance after a defined period.
3. **Terms Preservation:** Original LC terms must be preserved if amendment is not accepted.
4. **Status Updates:** LC status and amendment number must be updated only upon acceptance.
5. **Financial Updates:** Charges and provisions must be recalculated and updated upon acceptance.

---

## R8.5-UC5: Amendment Financial Impact

### Feature: Amendment Financial Impact Assessment

**As an** IPC (International Processing Center) Officer
**I want to** assess and manage the financial impact of approved amendments
**So that** provisions, charges, and accounting entries are correctly updated

**Background**
The amendment has been accepted by the beneficiary and is ready for financial processing.

#### 1. Scenario: Successfully update charges and provisions (Happy Path)
**Given** the amendment has been accepted by the beneficiary
**When** the IPC Officer clicks the **"Update Financials"** button
**Then** the system should recalculate charges based on the amended LC terms
**And** the system should recalculate provisions if the LC amount or risk profile changed
**And** the system should hold/release provisions in CBS as required
**And** the system should post appropriate accounting entries to the GL
**And** the system should display a success message: "Financials updated successfully."
**And** the system should show updated provision and charge amounts.

#### 2. Scenario: Charge calculation for increased LC amount (Edge Case - Calculation)
**Given** the amendment increases the LC amount from $100,000 to $150,000
**When** the system recalculates charges for the amendment
**Then** the system should apply the product charge template to the new amount
**And** the system should display the difference in charges between original and amended amounts
**And** the system should collect any additional charges from the applicant.

#### 3. Scenario: Provision adjustment for decreased LC amount (Edge Case - Calculation)
**Given** the amendment decreases the LC amount from $100,000 to $75,000
**When** the system recalculates provisions for the amendment
**Then** the system should reduce the held provision amount accordingly
**And** the system should release excess funds back to the applicant's credit line
**And** the system should post the reversal entry to the GL.

#### 4. Scenario: CBS integration failure during financial update (Edge Case - Integration)
**Given** the IPC Officer initiates financial update for an accepted amendment
**When** the CBS system is unavailable during provision hold/release
**Then** the system should display an error: "Unable to connect to CBS for financial update. Please try again later."
**And** the system should NOT apply the financial changes
**And** the system should log the error for troubleshooting
**And** the system should rollback any partial financial changes made during the update process.

### Business Rules & Validation
1. **Charge Recalculation:** Charges must be recalculated based on amended LC terms.
2. **Provision Adjustment:** Provisions must be adjusted if LC amount or risk profile changes.
3. **CBS Integration:** Financial updates require successful CBS integration for holds/releases and accounting.
4. **Error Handling:** Financial update failures must trigger appropriate rollback mechanisms.
5. **Audit Trail:** All financial changes related to amendments must be tracked.

---

## R8.5-UC6: Amendment History and Effective Terms

### Feature: Amendment History and Effective Terms Display

**As a** CSR (Customer Service Representative)
**I want to** view amendment history and effective terms for an LC
**So that** I can understand the current state and evolution of the LC

**Background**
The LC has gone through one or more amendment cycles.

#### 1. Scenario: View amendment history and effective terms (Happy Path)
**Given** the CSR is viewing an LC that has undergone amendments
**When** the CSR navigates to the **"Amendment History"** tab
**Then** the system should display a list of all amendments for this LC
**And** for each amendment, show: Amendment Number, Date, Status, Key Changes
**And** the system should display the **Original LC Terms** as initially issued
**And** the system should display the **Current Effective Terms** (original + all accepted amendments)
**And** the system should allow the CSR to drill down into any amendment for detailed view.

#### 2. Scenario: View LC with no amendments (Edge Case - Baseline)
**Given** the CSR is viewing an LC that has never been amended
**When** the CSR navigates to the **"Amendment History"** tab
**Then** the system should display a message: "No amendments have been made to this LC."
**And** the system should show the Original LC Terms as the Current Effective Terms
**And** the system should show an empty amendment history list.

#### 3. Scenario: Read-only enforcement in amendment context (Edge Case - UI Protection)
**Given** the CSR is viewing an LC from within an amendment context (e.g., reviewing amendment details)
**When** the CSR attempts to edit any field on the LC
**Then** the system should prevent the edit operation
**And** the system should display a warning: "LC details are read-only when viewed from amendment context."
**And** the system should allow editing only when viewing the LC directly (not from amendment context).

### Business Rules & Validation
1. **History Tracking:** All amendments (accepted, rejected, pending) must be tracked in history.
2. **Effective Terms:** Current effective terms must reflect original LC plus all accepted amendments.
3. **Original Terms Preservation:** Original LC terms must be preserved and visible for comparison.
4. **Context Awareness:** System must distinguish between direct LC viewing and amendment-context viewing.
5. **Read-only Protection:** LC must be read-only when viewed from amendment context to prevent data corruption.

---

## Summary

| User Case | Feature | Scenarios |
|-----------|---------|-----------|
| R8.5-UC1 | Create Amendment Request | 3 |
| R8.5-UC2 | Submit Amendment for Approval | 4 |
| R8.5-UC3 | IPC Approval and SWIFT Generation | 3 |
| R8.5-UC4 | Beneficiary Response Processing | 3 |
| R8.5-UC5 | Amendment Financial Impact | 4 |
| R8.5-UC6 | Amendment History and Effective Terms | 3 |

**Total Scenarios:** 21

---

*End of Document*
