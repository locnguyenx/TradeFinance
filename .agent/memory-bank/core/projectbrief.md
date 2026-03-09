# Project Brief

**Project:** Moqui Trade Finance - Import LC System
**Goal:** Build a complete Import Letter of Credit (LC) module on the Moqui Framework, complying with international business standards (UCP600, SWIFT).

## Core Objectives
- Manage the entire lifecycle of an Import LC: from Application, Issuance, Amendment to Drawing/Presentation and Payment.
- Integrate multi-level approval workflows between Branch and Head Office (IPC).
- Automate the generation of SWIFT messages (MT700, MT707).
- Ensure data consistency between the Moqui system and simulated Core Banking System (CBS).

## Scope
- **Import LC Module:**
    - Master LC management and related entities (Contracts, Documents).
    - Amendment process using a full Shadow Record architecture.
    - Drawing process including document examination and discrepancy handling.
    - Financial calculations for Charges and Provisions.
- **Interface:** Use Quasar/VueJS integrated within Moqui Screens, following a modern design with tabbed layouts and smooth workflows.

**Last Updated:** 2026-03-06
