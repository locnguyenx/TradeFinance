# Trade Finance System: User Guide for UAT

## 1. Introduction
Welcome to the Trade Finance System User Guide. This document is designed to guide users through the functionalities of the Import Letter of Credit (LC) module during User Acceptance Testing (UAT).

The system is built on the Moqui Framework and adheres to international trade standards:
- **UCP 600**: Uniform Customs and Practice for Documentary Credits.
- **SWIFT MT700**: Standard for issuing a Documentary Credit.

## 2. Dashboard & Navigation
Upon logging into the Trade Finance component, users are presented with the **Import LC Dashboard**.

### 2.1 Dashboard Features
- **Total Portfolio**: Displays the total number of Import LCs managed by the bank.
- **Active LCs**: Number of LCs currently in the `Issued` or `Amended` status.
- **Pending Workflow**: Tracks applications awaiting review or approval.
- **Expiring Soon**: Highlights LCs that will expire within the next 30 days.

### 2.2 Navigation
- Use the sidebar or dashboard links to navigate between:
    - **Dashboard**: Overview and quick stats.
    - **Find Import LC**: Search and browse existing LC records.
    - **New LC Application**: Start a new application process.

## 3. Import Letter of Credit Workflow

### 3.1 Applying for an LC (CSR Role)
To initiate a new LC application:
1. Navigate to **Find Import LC** and click the **Create New LC** button.
2. Fill in the required fields in the "Create LC" dialog:
    - **LC Number**: The unique reference for the LC.
    - **Amount & Currency**: The total value of the LC.
    - **Parties**: Select the Applicant (Importer), Beneficiary (Exporter), Issuing Bank, and Advising Bank.
3. Click **Submit Application**. This creates a record in `Draft` status.

### 3.2 LC Detail Management
Once created, clicking on the LC reference number opens the **Letter of Credit Detail** screen. This screen is the central hub for all LC-related activities, organized into sections:
- **General Information & MT700 Details**: Core LC terms.
- **Amendments**: History and management of LC changes.
- **Drawings & Presentations**: Tracking document sets and payments.
- **Documents & Attachments**: Scanned copies of applications, SWIFT messages, etc.
- **Audit Log**: A chronological history of all status changes.

### 3.3 Approval Process
Applications follow a standard workflow:
- **CSR**: Drafting and submitting the application.
- **Supervisor**: Initial review and approval.
- **IPC (International Processing Center)**: Final review, calculation of charges/provisions, and issuance.

The **StatusFlow** buttons (e.g., [Submit], [Approve], [Reject]) are located at the top-right of the LC Detail screen.

### 3.4 LC Issuance
Once the application is fully approved, the IPC officer clicks **Issue LC**. This action:
1. Transitions the LC status to `Issued`.
2. Generates the **SWIFT MT700** message.
3. Triggers accounting entries for provisions and commissions.

---
## 4. Post-Issuance Management

### 4.1 LC Amendments
Amendments are used to change terms of an already issued LC (e.g., increasing the amount or extending the expiry date).
1. Go to the **LC Detail** screen of an `Issued` LC.
2. In the **Amendments** section, click **Request New Amendment**.
3. Select the field to change, enter the new value, and provide remarks.
4. Click **Submit Request**.
5. The amendment request follows the same approval workflow (CSR -> Supervisor -> IPC).
6. Once approved, the LC status transitions to `Amended`, and an **MT707** SWIFT message is generated.

### 4.2 Drawings & Presentations
When the beneficiary presents documents to the bank, they are recorded as "Drawings".
1. In the **Drawings & Presentations** section of the LC Detail, click **Log New Drawing**.
2. Enter the **Drawing Amount** and **Presentation Date**.
3. Once logged, the drawing will appear in the list.
4. IPC officers can view the drawing details to record discrepancies or proceed to payment.

### 4.3 Discrepancy Handling
If presented documents do not match the LC terms:
1. IPC officers record findings in the **Discrepancies** panel of the Drawing Detail.
2. The bank may send an **MT734** (Advice of Refusal).
3. The Applicant can choose to **Accept**, **Reject**, or **Waive** the discrepancies via the system buttons.

### 4.4 Payment & Maturity
- **Sight Payment**: For LCs payable at sight, the system generates an **MT756** and triggers accounting once the documents are accepted.
- **Usance LC**: For LCs with credit terms, the system calculates the **Maturity Date**. Payment is scheduled for that date, and the bank enters an "Acceptance" phase.

## 5. Static Data & Configuration
The system automatically handles complex logic based on predefined templates:
- **Charges**: Issuance, amendment, and discrepancy fees are calculated based on the product template.
- **Provisions**: A percentage of the LC amount is automatically held as collateral upon issuance.
- **LC Clauses**: Standard clauses for document requirements can be managed and selected during application.

## 6. Common Scenarios & Troubleshooting
- **Missing Required Fields**: SWIFT validation rules will prevent submission if critical fields (like Beneficiary or Expiry) are empty.
- **Insufficient Funds**: The system integration with Core Banking will flag issues if the applicant's account cannot cover provisions and charges.
- **Expired LC**: A daily job identifies LCs past their expiry date and updates their status to `Expired`.

## 7. Support
For technical issues during UAT, please contact the IPC Technical Support team or refer to the internal Knowledge Base.
