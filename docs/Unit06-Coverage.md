# Unit 6 coverage

Reference: the supplied `Unit06_SystemSpecificationPresentation.pdf` (17 pages).
Document: [LeoWay system specification](LeoWay-System-Specification.md), version 0.7.

| Handout pages | Topic | Where it is covered |
| --- | --- | --- |
| 2-3 | Purpose and six-part outline | Sections 1-6 follow the outline: initial situation, features, quality, quantities, architecture/interfaces and acceptance. |
| 4 | Application domain and terminology | Sections 1.1-1.2 describe school commuting, WebUntis, transport operators and the terms used. |
| 4 | Current business processes and detailed goal | Sections 1.3-1.4 describe BP1-BP3, today's delay-appeal flow, goals and project limits. |
| 5-6 | Grouped use cases with overview diagrams and descriptions | Sections 2.1-2.4 contain four blocks. Each starts with a short description and a use-case diagram. |
| 7 | Six characteristic fields per use case | All F01-F12 list the linked business process, goal, precondition, postcondition, roles and trigger. |
| 8 | Entry GUI, normal scenario/GUI and error scenario/GUI | Each F01-F12 has a Call, Standard and Non-standard sketch, plus both written scenarios. Sketch values are illustrative. |
| 8 | Activity diagrams | Every F01-F12 has its own activity diagram. Section 1.3 also shows the current appeal process. |
| 9-10 | Specific usability for the target group | Section 3.1 specifies student phone views, the departure-board layout, keyboard use and teachers opening links in a browser. |
| 11 | Measurable efficiency | Section 3.2 gives E01/E02, including response time, concurrent test users and processing time. They are provisional targets, not measured performance. |
| 12 | Maintainability and later extension | Section 3.3 covers handover documentation, CI, separate source adapters and notification channels. |
| 13 | Confidentiality, integrity and availability | Section 3.4 names all three, with access checks, protected keys, evidence integrity, independent adapters and restore testing. It also covers restricted Auspex execution. |
| 14 | Legal and domain rules | Section 3.5 names GDPR/DSG, links official guidance, describes data handling, source conditions and the teacher's decision. Permission-request tasks are removed. |
| 15 | First quantity/resource estimates | Section 4 estimates master records, daily departures, storage and transfer volumes with assumptions and calculations. Optional model size remains unknown until a model is selected. |
| 16 | Clients, services and communication | Sections 5.1-5.2 contain a component diagram and an interface table with protocols, exchanged data and external partners. |
| 17 | Acceptance based on use-case scenarios | Section 6 maps A01-A12 to F01-F12 and checks normal and error cases. A13-A14 cover security and measurable quality. |

## Scope kept from the team's decisions

- Model training, Python and the teacher portal are optional.
- Product owner: Behrang Elyasi. Final delivery: January 2028.
- iPhone users use the website.
- The separate general home-screen sketch and main-data-objects/domain-model section remain removed. The small use-case sketches cover the GUI requirement on handout page 8.
- Sizing figures and performance targets are first planning values to revise with measurements; they are not claims about real users or source volumes.
- This document describes planned behaviour. It does not claim these features have already been implemented or tested in the running application.
