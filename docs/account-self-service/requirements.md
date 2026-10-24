# Account self-service requirements
- WHEN a member invokes Discord /account, THE SYSTEM SHALL privately show only that member's current linked Minecraft identity and relinking instructions.
- WHEN a member requests unlink, THE SYSTEM SHALL require an actor-bound single-use confirmation valid for two minutes and explain linked-access loss.
- IF the link changed, the token expired, another actor clicks, or storage fails, THE SYSTEM SHALL preserve the replacement link and report an accurate outcome.
- WHEN a confirmed unlink commits, THE SYSTEM SHALL retain DiscordSRV lifecycle notifications, console hooks, and require-link enforcement.
- WHEN Discord direct messages are enabled, THE SYSTEM SHALL accept exact account and unlink help keywords without interpreting arbitrary chat as commands.
- THE SYSTEM SHALL preserve administrator commands, Staff vanish integration, existing link codes and global relink policy.

No production deployment, restart, shared TEST use, or actual account mutation is authorized by implementation. Discord command visibility still requires an explicit integration allow at deployment. Existing /discord unlink remains available. A self-only /unlink shortcut uses click-to-confirm with the same expiry, actor binding and exact pair guard; it accepts no target account.
