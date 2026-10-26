# Lamplight

An independent home theme for a television used from the sofa. A low-luminance aubergine field keeps a dark room comfortable; warm ivory labels and a solid amber focus mark give the remote a single unmistakable destination. Images belong to supplied apps and media, never to decorative backgrounds. The signature is the amber perimeter and matching caption: the selected object remains itself, with no enlargement or image-obscuring overlay.

The product record comes solely from the supplied brief. The user delegated direction and selected direct code construction; that replaces the skill's interview and visual decision page. No Free My TV repository, worktree, incumbent theme, screenshot, or device was inspected. Platform fonts and geometric icons are explicit constraints. Canvas drawing is explicitly requested; native View accessibility must be provided by the host.

## Layout and behavior

Coordinates are reference pixels on a centered, uniformly scaled 1920×1080 plane. Unequal aspect ratios receive background-colored letterboxing, never stretched images. Safe horizontal limits are x=80..1840. Header stays fixed; the scrolling region is y=174..1010. No animation is required, so system reduced-motion needs no special path.

- Home title: baseline112, 52px; supplied local time: right aligned at1840, baseline80, 34px. Supplied weather occupies the right header, with temperature above condition and location. Missing weather/time is omitted.
- Apps heading y174; app images y232, 260×150, 28 gap, captions below. App logos fit completely with internal padding. Six complete apps fit; more scroll horizontally.
- Controls y444; 332×78 buttons, 24 gap; labels and geometric icons. Host supplies real Inputs, Settings and Google TV actions.
- Continue watching heading y590; 416×234 images at648, 28 gap, title/detail below; known progress is drawn inside the image edge. Unknown progress is omitted.
- Discovery heading y966; images at1024, same media dimensions. This section enters through vertical D-pad navigation rather than shrinking all content to fit one screen.
- Header rule and footer occupy fixed zones. Maximum content scroll338. Focus reveal scrolls enough to show the complete card, caption, detail and heading. Each horizontal row retains its own offset and remembered index.

Left/right moves among enabled items without wrapping. Up/down moves to the adjacent row and restores its remembered enabled index. Empty/error/loading or disabled-only rows receive section focus (focusedItem=-1), remain reachable and reveal their state. Boundaries retain focus. Replacing data calls normalize to clamp stale focus and scrolling. Empty, loading and error rows use provider messages or restrained functional fallbacks. Cached media can remain visible during loading/error, with update status shown beside its heading. Focus is a 4px amber outline with6px clearance; action focus also fills amber with dark text. Disabled items are dimmed and cannot activate.

Titles and detail text ellipsize to one line, respecting UTF-16 surrogate pairs and normalizing newlines. textScale accepts1..1.3; host should map its font preference into that range. This is a TV-specific bounded layout, not unrestricted Android text-scale support. Default labels are28px, details24px, section headings32px on the reference plane. Do not advertise support for smaller viewports before native viewing-distance tests.

## Integration

Copy ImpeccableHomeRenderer.java into the io.freemytv Java source package. No AndroidX, Compose, network library or external font is required. Create one renderer and one Model on the UI thread. In View.onDraw call renderer.render(canvas,getWidth(),getHeight(),model). Set each Row using real provider data; image bitmaps must already be decoded off the UI thread. Do not recycle a bitmap while the View can draw it. Rows defensively copy their list, though Bitmap ownership remains with the host.

Use stable Item.id values for real launch targets. Item.title/detail are localized display strings. Applications, settings, recovery and playback remain host actions; this renderer performs no network, package, installation or configuration operations. If replacing lists while the user is browsing, preserve selection by stable ID before normalize; index alone cannot preserve identity after a provider reorder.

Make the host View focusable and handle ACTION_DOWN direction keys with move(model,keyCode), then invalidate. Handle DPAD_CENTER/ENTER by obtaining focused(model) and launching its real target through your existing checked dispatch. Handle launch failures with an accessible host message and a recovery action; do not swallow exceptions. Keep system Back behavior in the Activity; move does not consume Back. Never implement a Back loop that traps the user in this launcher.

Always provide a verified enabled Google TV action within model.actions, ordered before secondary controls, and retain the existing out-of-launcher recovery procedure. Resolve the installed Google TV activity through the host's existing logic; no package name is guessed here. Renderer-only delivery cannot guarantee recovery configuration. Do not disable, uninstall, or alter the system launcher.

Provide an AccessibilityNodeProvider (or the existing host accessibility layer) with one virtual node per enabled item, stable IDs, title, detail, role, selected state and ACTION_CLICK. Use itemBounds for current visible physical coordinates, and send accessibility focus/selection events on remote movement. Include supplied weather and time as readable informational nodes. Keep the time informational rather than announcing every clock update. Canvas alone does not expose semantic nodes and is not a completed screen-reader implementation.

## Validation and limits

Code/layout review found and corrected unreachable empty discovery states and long-time alignment; the review scope did not include native visual rendering. Java compilation passed against the locally installed Android34 SDK using Homebrew javac. Android12 compatibility is maintained through SDK-only APIs available by API26. No TV was connected, changed or used. No device/emulator screenshot exists: layout, actual font metrics, bitmap rendering, screen-reader semantics, overscan, contrast on the panel and long-label behavior still need native integration verification. This is a standalone renderer, not an installed or fully integrated launcher.

Use real supplied content for a native review at1920×1080 and the device's actual framebuffer; cover every D-pad direction, empty rows, cached-error rows, many items, long and RTL titles, disabled controls,1.3 text scale and aspect-ratio letterboxing. RTL layout mirroring is not implemented; host integration must add it if required. The design does not fabricate poster titles or app data to produce screenshots.
