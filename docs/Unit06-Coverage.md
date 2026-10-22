# Unit 6 coverage

Reference: the supplied `Unit06_SystemSpecificationPresentation.pdf` (17 pages).
Document: [LeoWay system specification](LeoWay-System-Specification.md), version 1.0.

| Handout pages | Topic | Where it is covered |
| --- | --- | --- |
| 2-3 | Purpose and six-part outline | Sections 1-6 follow the outline: initial situation, features, quality, quantities, architecture/interfaces and acceptance. |
| 4 | Application domain and terminology | Sections 1.1-1.2 describe school commuting, WebUntis, transport operators and the terms used. |
| 4 | Current business processes and detailed goal | Sections 1.3-1.4 describe BP1-BP3, today's delay-appeal flow, goals and project limits. |
| 5-6 | Grouped use cases with overview diagrams and descriptions | Sections 2.1-2.4 contain four blocks. Each starts with a short description and a use-case diagram. |
| 7 | Six characteristic fields per use case | All eleven user use cases (F01-F05 and F07-F12) list the linked business process, goal, precondition, postcondition, roles and trigger. |
| 8 | Entry GUI, normal scenario/GUI and error scenario/GUI | Each of the eleven user use cases has a Call, Standard and Non-standard sketch, plus both written scenarios. Sketch values are illustrative. |
| 8 | Activity diagrams | Every user use case has its own activity diagram. F06 separately describes internal backend integration and its processing flow; it has no user GUI. Section 1.3 also shows the current appeal process. |
| 9-10 | Specific usability for the target group | Section 3.1 specifies student phone views, the departure-board layout, keyboard use and teachers opening links in a browser. |
| 11 | Measurable efficiency | Section 3.2 gives E01/E02, including response time, concurrent test users and processing time. They are provisional targets, not measured performance. |
| 12 | Maintainability and later extension | Section 3.3 covers handover documentation, CI, separate source adapters and notification channels. |
| 13 | Confidentiality, integrity and availability | Section 3.4 names all three, with access checks, protected keys, evidence integrity, independent adapters and restore testing. It also states that Auspex is backend only and cannot be used directly by students or teachers. |
| 14 | Legal and domain rules | Section 3.5 names GDPR/DSG, links official guidance, describes data handling, source conditions and the teacher's decision. Permission-request tasks are removed. |
| 15 | First quantity/resource estimates | Section 4 estimates master records, daily departures, storage and transfer volumes with assumptions and calculations. Optional model size remains unknown until a model is selected. |
| 16 | Clients, services and communication | Sections 5.1-5.2 contain a component diagram and an interface table with protocols, exchanged data and external partners. |
| 17 | Acceptance based on use-case scenarios | Section 6 maps A01-A12 to F01-F12: user features have normal and error checks; A06 checks the internal backend boundary. A13-A14 cover security and measurable quality. |

## Scope kept from the team's decisions

- Model training, Python and the teacher portal are optional.
- Auspex is backend only. There is no student scripting feature.
- Section 1.1 explicitly describes the application domain, users, activities and system boundary.
- The document is version 1.0 and is not labelled as a draft.
- Product owner: Behrang Elyasi. Final delivery: January 2028.
- iPhone users use the website.
- The separate general home-screen sketch and main-data-objects/domain-model section remain removed. The small use-case sketches cover the GUI requirement on handout page 8.
- Sizing figures and performance targets are first planning values to revise with measurements; they are not claims about real users or source volumes.
- This document describes planned behaviour. It does not claim these features have already been implemented or tested in the running application.
