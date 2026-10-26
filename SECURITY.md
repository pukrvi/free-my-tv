# Security reporting

Vibe TV is under active development. No maintained release/version support window has been established. The inherited Original mode and watchdog are recovery mechanisms, not a guarantee against every firmware-specific failure.

Please report suspected vulnerabilities privately using GitHub's **Report a vulnerability** option if available for this repository. If it is unavailable, open a minimal issue asking a maintainer for a private reporting channel without including exploit details, credentials or sensitive device information. Do not assume a private email address or response deadline exists.

Include affected commit/build, exact TV model and OS, impact, reproduction steps and sanitized evidence. Avoid publishing details that would put other users' TVs at risk before maintainers can assess them. Ordinary app compatibility failures belong in the bug/hardware templates.

Never attach signing keys, `config.properties`, API tokens, pairing codes or full environment files. If a credential is exposed, revoke or rotate it with its provider; deleting a comment alone does not revoke it.
