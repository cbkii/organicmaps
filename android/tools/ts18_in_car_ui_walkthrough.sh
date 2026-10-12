#!/data/data/com.termux/files/usr/bin/bash
# Human-operated qualification. Does not inject input or mutate Android state.
umask 077
case "${1:-}" in
  -h|--help) printf 'Usage: bash ts18_in_car_ui_walkthrough.sh [--no-capture]\nP = pass, F = fail, N/Enter = not run, Q = finish. Optional comments.\n'; exit 0 ;;
  ''|--no-capture) ;;
  *) printf 'Unknown argument. Use --help.\n' >&2; exit 2 ;;
esac
if [ ! -t 0 ]; then printf 'Interactive terminal required; no tests were run.\n' >&2; exit 2; fi
output="${EXPORT_BASE:-/storage/emulated/0/Download/OrganicMaps-TS18-UX-Qualification}"
run="$output/$(date -u +%Y%m%dT%H%M%SZ)-walkthrough-$$"
mkdir -p "$run" || { printf 'Cannot export results. Run termux-setup-storage first.\n' >&2; exit 3; }
collector="$(cd "$(dirname "$0")" && pwd)/ts18_termux_in_car_ux_capture.sh"
results="$run/MANUAL.tsv"
printf 'test\tresult\tcomment\n' > "$results"
finish() {
  trap - EXIT INT TERM
  while IFS='|' read -r key instruction; do
    if ! awk -F '\t' -v key="$key" 'NR > 1 && $1 == key {found=1} END {exit !found}' "$results"; then
      printf '%s\tNOT_RUN\tNot reached before finish\n' "$key" >> "$results"
    fi
  done <<< "$steps"
  (cd "$run" && sha256sum MANUAL.tsv > MANIFEST.sha256 && sha256sum -c MANIFEST.sha256)
  printf '\nManual results: %s\nCollector PASS only means evidence capture succeeded.\n' "$results"
}
steps='identity|Park safely. Confirm APK package/version/commit match the candidate and keep the previous same-signer APK. Check cold launch, permissions and map render. No firmware or protected-package changes.
rhd|Open In-Car Settings > Map & Display. Default RHD puts controls left. Check rail, Search/Menu, Quick Destinations, place/planning panels and footer fit without overlap.
lhd|Choose LHD. Check applicable controls move right, group order stays intact, banner stays informational. Restart app and confirm persistence; restore desired setting.
rail|Check exactly four cells: Zoom +, Zoom -, Follow Mode, Camera View/Recentre; three dividers; one surface. Check both zoom actions; no duplicate camera/Driving/recording FAB. Recording remains in menu.
follow|With usable location, tap third cell through native eligible Follow modes. Pan map, then resume following. Check actual icons/accessibility match pending, unavailable, free, Follow and FollowAndRotate. Skip bearing-dependent states if stationary bearing is unavailable.
camera|Tap fourth cell twice: actual top-down > tilted > top-down, recentring each time. Repeat after panning, in Driving View and active navigation. Check Follow remains independently usable. Do not infer perspective from icon alone.
recovery|If safely reproducible while parked, observe missing/stale GPS and permission/settings handling. Check bounded recovery, no invented position or repeated prompts. Never revoke permissions merely to qualify without recording/restoring original state.
browse|Check Search + Menu only; Saved Places in menu. Check day/night contrast, pressed/focused controls, long names and Quick Destinations expansion. No duplicate Search during navigation.
go-to|Select a new eligible place. GO TO stays prominent/direct even compact; all other actions reachable via More. Default 10-second countdown keeps label visible; wait for one route build. Repeat and touch/pan/open menu to cancel; no redraw rearm. Manual GO TO works.
start|For genuinely built ready route, wait for independent START countdown and one normal START dispatch. Repeat with manual START and cancellation. Required disclaimers/settings remain manual. Edit/rebuild route: old callbacks must not start obsolete route.
delays|Check all delay choices 2/3/5/10/15/20/30 seconds are present and persist. Test chosen delay for each stage; restore desired value. Untested individual delays remain NOT_RUN in comment.
lifecycle-cancel|During each countdown, background/return, open dialog, change window or turn screen off. No stale dispatch or silent rearm. New eligible selection/route may arm anew. Check keyboard/accessibility interaction cancellation where available.
hud|During real/test navigation, compare day/night NOW > LANES > NEXT hierarchy; active/inactive lanes, glyphs and text readable; LANES/AFTER conditional behaviour preserved.
speed|Parked-only approved injected/test data: check 105% orange, 110% red, hysteresis clears near 103/108%, full-width ~4dp underline, normal hidden. Never speed or drive to provoke warnings. No approved data? N. Check stale/unknown/unrestricted limit and END reset only if reproducible.
footer|Check full and reduced windows: ~460-540dp when space permits, complete distance/time with units and long localisation, no clipping; cog opens existing voice+Settings menu, END works, progress preserved.
map-style|Inspect actual rendered day/night route cyan core/deep-blue casing, arrows/alternatives/traffic preserved. POI/park/water labels gold/charcoal; road/locality/shields unchanged; house numbers smaller with original colours. Choose non-private screenshot area.
window|Switch launcher-hosted window/fullscreen/Home/return repeatedly. Check fit, insets, hit targets, renderer/touch alignment, rail and footer collision. Test route build > START > guidance > END in each supported size.
restart|Restart app, reopen previous route where supported. Check location, camera truth, follow, settings and manual navigation; no duplicate tasks or controls. Installation is not physical validation.
reboot|Only when convenient and safe: normal Android reboot, launch and repeat core navigation/rail checks. N if unavailable. No firmware/recovery procedure.
acc|Only on actual unit when safe: normal ACC sleep/wake, then check warm return/location/camera/navigation. Cold boot and reverse-camera transitions separately in comment; N for untested boundaries.'
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
printf 'Park first. Test manually in Organic Maps, return to Termux, then record P/F/N.\nScreenshots/logs can reveal locations; inspect before sharing. Q seals partial results.\n'
while IFS='|' read -r key instruction; do
  printf '\n[%s] %s\n' "$key" "$instruction"
  while true; do
    printf 'Result P/F/N (Enter = N), Q = finish: '
    IFS= read -r answer < /dev/tty || exit 0
    case "$answer" in
      p|P) verdict=PASS; break;; f|F) verdict=FAIL; break;; ''|n|N) verdict=NOT_RUN; break;; q|Q) exit 0;;
      *) printf 'Use one letter P, F, N or Q.\n';;
    esac
  done
  printf 'Optional comment (Enter skips): '
  IFS= read -r comment < /dev/tty || comment=''
  comment="${comment//$'\t'/ }"
  printf '%s\t%s\t%s\n' "$key" "$verdict" "$comment" >> "$results"
  if [ "$verdict" = FAIL ] && [ "${1:-}" != --no-capture ]; then
    printf 'Capture current state? Y or Enter skips: '
    IFS= read -r capture < /dev/tty || capture=''
    if [ "$capture" = y ] || [ "$capture" = Y ]; then
      if [ -f "$collector" ]; then bash "$collector" "$key-failure" || printf 'Evidence incomplete; manual failure still recorded.\n';
      else printf 'Collector absent: evidence BLOCKED; manual result retained.\n'; fi
    fi
  fi
done <<< "$steps"
