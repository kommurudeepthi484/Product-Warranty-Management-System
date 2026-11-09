# WarrantyHub - Product Warranty Management System
Java backend (REST API) + HTML/CSS/JS frontend. No Maven, no database install, no libraries.

## Run in VS Code
1. Install JDK 17+ and the "Extension Pack for Java" in VS Code.
2. File > Open Folder > select this `warranty-system` folder.
3. Open `src/Main.java` and click "Run" above `public static void main` (or press F5).
4. Open http://localhost:8080 in your browser.
(No VS Code? Double-click `run.bat` on Windows, or run `./run.sh` on Mac/Linux.)

## Requirement -> where it is built
| Requirement | Backend (src/Main.java) | Frontend |
|---|---|---|
| 1 Records | addProduct, addCustomer | Products, Customers pages |
| 2 Registration | addRegistration | Register Warranty page |
| 3 Warranty status | Registration.isActive() (computed live from the date) | Badges, progress bars, rings |
| 4 Claims | addClaim | Check & Claims page |
| 5 Eligibility + tracking | Active-only rule in addClaim; updateClaim | Disabled submit, timeline, Approve/Reject |
| Extra: search + filter | listRegistrations, listClaims | Search boxes + status chips |

## Innovation features
Live eligibility check while typing a serial | Warranty ring and progress bar | Expiring-soon alerts (30 days)
Claim timeline with notes | Live auto-refresh + toast when a claim changes | Shop Owner / Customer portals
Printable warranty certificate | Duplicate-serial protection | Data auto-saved (data.ser)

## 4-minute demo script
1. Click "Load demo data". Show the Dashboard.
2. Register Warranty: pick a product and watch the live preview.
3. All Warranties: search "ravi", filter Expired.
4. Switch to Customer. Enter SN-LG-2002: Expired, so submit is blocked.
5. Enter SN-S24-1005: Active, so submit a claim.
6. Switch back to Shop Owner, Approve it, and show the timeline.
