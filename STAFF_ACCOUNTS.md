# Staff Accounts

Internal. Never publish. Change before any real deployment.

| Role | Username | Password | What it can access |
| :--- | :--- | :--- | :--- |
| Chef | `chef_mario` | `ChefPassword123!` | Kitchen display board and order status transitions (`PLACED → ACCEPTED → PREPARING → READY`) |
| Waiter | `waiter_john` | `WaiterPassword123!` | Billing and payment settlement for open table sessions |
| Manager | `manager_alice` | `ManagerPassword123!` | Everything: kitchen board, billing, and the admin console (menu, categories, tables, reports) |

## Notes

- Passwords are stored in the database as BCrypt hashes (`db/seed.sql`); the values above are the plain-text originals for local development only.
- This file lives outside `src/`, so it is never served by the application.
- Rotate or replace every password above before exposing the system to anyone.
