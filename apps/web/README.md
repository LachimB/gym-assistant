# Web simulator

Local browser dry-run of the synthetic composite workout. It does not record a Workout Session and it does not call any hosted service.

```bash
cd apps/web
npm install
npm test
npm run build
npm run dev
```

Open the URL Vite prints. The default is `http://127.0.0.1:5173`.

`npm run build` type-checks the source (including fixture-loading tests) and emits
the production bundle in ignored `dist/`. Node 22 declarations are a direct dev
dependency because those tests use `node:fs`, `node:path`, and `import.meta.dirname`.
CI runs this build after the web tests. For a clean dependency check, use `npm ci`.

Use **Virtual +1s** when comparing the screen with the 50,000 ms golden timeline. **Use wall clock** moves the scheduled clock from the browser timer while the tab is in the foreground. A background tab is not evidence. On-screen cue text is simulated audio, not speech synthesis.

While wall-clock preview is enabled, Pause freezes scheduled/observed running
time and accumulates the separate pause counter. Resume excludes that paused
interval. Turning the wall clock off stops automatic accounting; virtual advances
still work. Fractional frame intervals are carried rather than rounded per frame.
