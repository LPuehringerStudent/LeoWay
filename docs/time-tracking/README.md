# Time tracking

Hours are logged in the teachers' time-tracking Excel on SharePoint during the sprint. The link comes from the
teachers and is not in this repository, because the repository is public.

At the end of each sprint a copy goes into a folder `Sprint<N>/` in the sprint wrap-up PR:

- `Sprint<N>.xlsx`: the whole sheet, one tab per person
- `Sprint<N>-Person<n>-<Name>.csv`: one file per person, exported from that tab (semicolon-separated, UTF-8)

Each row is one block of work: date, start and end time, task, GitHub issue numbers and minutes. The last row of each
CSV has the total.
