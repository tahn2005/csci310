# Project 2.3 revised design diagrams

PlantUML sources (`.puml`) and rendered images (`.png`) for the Project 2.3 implementation
document. Each diagram marks its departures from Project 2.2: **green** text = added or
changed, **red struck-through** text = removed, green-tinted box = new class, yellow box =
changed class. Unmarked elements are unchanged from 2.2.

Re-render after editing (needs `plantuml`): `plantuml -tpng *.puml`

| Figure | File | Shows |
|---|---|---|
| 4.1 | `arch_block` | Revised architectural configuration |
| 5.1a / 5.1b | `cd1a_entities_ride`, `cd1b_entities_requests` | Domain entities |
| 5.2a / 5.2b | `cd2a_query_objects`, `cd2b_utilities` | Query objects and utilities |
| 5.3a / 5.3b | `cd3a_repository_interfaces`, `cd3b_firestore_classes` | Repositories |
| 5.4 | `cd4_gateways` | Auth and Location gateways |
| 5.5 | `cd5_logic_a` | Account Service, Ride Service, Ride Search Engine, Session |
| 5.6 | `cd8_history` | Ride History Service |
| 5.7 | `cd6_join` | Join Request Service |
| 5.8 | `cd7_cost` | Cost Split Service |
| 5.9a–c | `cd9a_ui_navigation`, `cd9b_ui_account`, `cd9c_ui_ride_post` | Navigation Shell, Account & Profile UI, Ride Post UI |
| 5.10 | `cd10_ui_search` | Search & Requests UI |
| 5.11 | `cd11_ui_cost` | Cost Split UI |
| 5.12 | `cd12_ui_history` | History & Summary UI |
| 5.13a / 5.13b | `cd13a_ui_base_classes`, `cd13b_ui_views` | Shared presentation helpers (new) |
| SD-1 | `sd1a_signup_validate`, `sd1b_signup_save` | Sign Up |
| SD-2 | `sd2a_create_ride_places`, `sd2b_create_ride_post` | Create a Ride Post |
| SD-3 | `sd3a_join_check`, `sd3b_join_send` | Request to Join a Ride |
| SD-4 | `sd4a_split_preview`, `sd4b_split_save` | Complete a Ride and Split the Cost |
| SD-5 | `sd5a_history`, `sd5b_monthly_summary` | Ride History and Monthly Summary |
