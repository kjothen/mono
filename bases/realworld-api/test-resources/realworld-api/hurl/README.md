# RealWorld conformance suite

Vendored from [gothinkster/realworld](https://github.com/gothinkster/realworld),
`specs/api/hurl`, unmodified.

Vendored rather than fetched at run time so a run is reproducible and a
change upstream shows up as a diff to review rather than as a surprise
failure. Refresh deliberately.

The suite is the contract. Where the prose specification and these
assertions disagree, upstream says the tests win — so an exact string here
outranks anything in our own docstrings.

Run with `just realworld-hurl`, which starts postgres and the service and
points the suite at it. Every fixture is namespaced by a `uid` variable, so
a repeat run against the same database is safe and no reset is needed.
