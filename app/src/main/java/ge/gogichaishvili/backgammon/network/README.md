# Future network boundary

No transport is installed in this milestone. Future Supabase adapters belong in data/network,
behind domain repository contracts for authenticated sessions and authoritative matches.
UI and engine must never depend on Supabase DTOs. Validate remote events before applying
them to engine state; visual dice are never authoritative. Introduce concrete contracts
when match workflows are specified, rather than speculative unused interfaces.
