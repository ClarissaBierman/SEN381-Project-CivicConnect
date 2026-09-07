Our team manages all controlled project artefacts and code through a single shared GitHub repository.
Since the whole point of this milestone is to show a controlled process rather than just handing in a finished document, we set the repository up properly from the start to avoid a large amount of commits before the deadline.
The main branch is protected.
None of us can push directly to it, even by accident, because direct commits are switched off at the repository settings level.
Any change, whether it's an update to the PED, the risk register, or later on actual code, has to go through a pull request.
Before a pull request can be merged it needs approval from two other team members, not just one, and none of us can approve our own work.
We also made sure the setting can't be bypassed, including by whoever owns the repository, so the rule actually holds under pressure rather than being something we could quietly skip closer to the deadline.
Reviewing a pull request means actually reading the change and commenting on it, not just clicking approve.
When the other members review something I've submitted, or vice versa, the expectation is that they check it makes sense against what we've already agreed, that it's consistent with the rest of the document, and that nothing important is missing or wrong.
An approval with no comment on a substantial change doesn't count as a real review under our own process, even if GitHub would technically allow it.
We're using GitHub Issues to track the outstanding work for Milestone 1, one issue per section (Scope Baseline, Requirements, Constraints, RTM, Risk Register, and so on), assigned to whoever owns that section.]
This gives us a visible record of who is doing what and when it actually got done, rather than relying on us remembering it after the fact.
No passwords, API keys, or credentials are committed to the repository at any point, and since we haven't picked a technology stack yet that risk is currently low anyway, but the rule stands regardless of what we build later.
Finally, the repository history is meant to build up gradually across the milestone.
We're deliberately avoiding the situation where all our commits appear the night before the deadline, since that wouldn't actually demonstrate a controlled process, it would just look like one after the fact.

