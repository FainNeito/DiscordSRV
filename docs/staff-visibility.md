# EnthusiaStaff visibility repair (SPEAR)

## Spec

- WHEN EnthusiaStaff is present and enabled as a hook THEN THE SYSTEM SHALL
  register its public Bukkit visibility adapter instead of SuperVanish.
- WHEN the Staff service is missing, disabled, unloaded or fails THEN THE SYSTEM
  SHALL hide subjects without terminating nickname, topic or presence workers.
- WHEN a player is hidden by vanish or staff-duty visibility THEN THE SYSTEM
  SHALL exclude them from public Discord surfaces using the existing VanishHook
  call sites; stored state, permissions and synthetic join/quit behavior remain
  unchanged.
- WHEN EnthusiaStaff is absent or explicitly hook-disabled THEN THE SYSTEM SHALL
  preserve existing hook selection, rather than pretending SuperVanish support.

## Proof / engine / architecture / refine

Base: freshly cloned and fetched authoritative master da9e027f7acd76e0f91d53a11e5b53832ac36e92
(1.30.5). CONTRIBUTING requires upstream PRs to target develop; no upstream PR or
production deployment is authorized or claimed here. A maintained owner fork is
needed for reviewable Enthusia delivery.

Runtime red evidence: nickname, presence and topic updater threads throw
NoClassDefFoundError for de.myzelyam.api.vanish.VanishAPI. Installed SuperVanish
has a different namespace. PlayerUtil bytecode directly iterates VanishHook.

Implementation is a read-only infrastructure adapter using Staff's published
service interface via its owning plugin classloader, with a fresh provider lookup
per request. Public canSee uses an unregistered zero-UUID audience and guards that
reserved identity. Staff-duty and vanish remain distinct states. Selection occurs
inside DiscordSRV's existing startup hook loop, never by mutating a live HashSet
from the image add-on. No fake SuperVanish classes or database writes are used.

No project EARS/state helpers exist. This requirement/task/evidence record does
not claim automated SPEAR tooling passed.

- [x] Focused Staff hook tests and clean Gradle test/shadowJar build: 12 tests
  pass, including 8 new Staff hook tests. License formatting also passes/applies.
  Source 8/deprecation/annotation-processor warnings remain visible.
- [ ] Compile against actual companion Staff and runtime DiscordSRV APIs.
- [ ] Reviewable owner fork PR, exact-head checks/review, merge and source-build.
- [ ] Authorized SMP Test restart; updater survival, visible/vanished/on-duty
  sessions, provider-failure privacy, counts/lists and Discord/client acceptance.

Already terminated threads require a normal restart after installing a reviewed
artifact. Do not use plugin hot-reload or assume config reload revives them.
Production stays untouched.

2026-10-06 release review: upstream master was freshly fetched; no newer
upstream master commits require integration. The owner fork's master-targeted
PR had no hosted verification because upstream PR CI targets develop. Add a
read-only clean test/shadowJar/spotlessCheck job for this owner release PR;
publishing is not part of this job. Exact-head hosted verification and fresh
substantive review remain required. No merge or installation claimed yet.

Architecture refinement: hook selection respects explicit DisabledPluginHooks
prefixes, but a present-yet-disabled Staff provider retains the fail-closed
guard. Provider replacement is looked up freshly; failures warn once until
successful recovery. Only SuperVanish is bypassed after the guard is registered;
Essentials and other existing hooks remain unchanged.
