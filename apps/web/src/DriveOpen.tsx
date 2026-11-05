import { useEffect, useRef, useState } from 'react';
import { PersonalDriveReader, privateConnection, DriveReadError,personalDestination,type DriveConfig } from './PersonalDrive';
import { loadDriveDialogs } from './PersonalDriveDialogs';
import type { ReadHistory } from './SessionHistory';

export type DriveOpenHandle = { cancel(): void };
export function DriveOpen({onStart, onOpen, onClear, register}: {onStart: () => number;
  onOpen: (history: ReadHistory, account: string, request: number) => void; onClear: () => void;
  register: (handle: DriveOpenHandle) => void}) {
  const [config, setConfig] = useState<DriveConfig|null>(null);
  const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false);
  const abort = useRef<AbortController | undefined>(undefined); const reader = useRef<PersonalDriveReader | undefined>(undefined);
  const generation = useRef(0);
  function cancel() { ++generation.current; abort.current?.abort(); abort.current = undefined; reader.current?.disconnect();
    reader.current = undefined; setConfig(null); setBusy(false); setMessage(''); }
  useEffect(() => { register({cancel}); return () => { ++generation.current; abort.current?.abort(); reader.current?.disconnect(); }; }, []);
  async function prepare() {
    cancel(); onClear(); const current = ++generation.current;
    const controller = new AbortController(); abort.current = controller; setBusy(true);
    setMessage('Preparing the approved personal connection…');
    const timeout = setTimeout(() => controller.abort(), 45000);
    try {
      const connection = await privateConnection(controller.signal);
      const dialogs = await loadDriveDialogs(controller.signal);
      if (controller.signal.aborted || current !== generation.current) return;
      reader.current = new PersonalDriveReader(connection.config, connection.consume, dialogs);
      setConfig(connection.config); setMessage('Choose one delivered recording from the personal folder.');
    } catch (error) { if (current === generation.current) setMessage(error instanceof DriveReadError ? error.message : 'Unable to prepare personal Drive access.'); }
    finally { clearTimeout(timeout); if (current === generation.current) setBusy(false); }
  }
  async function choose() {
    if (!reader.current || !config || busy) return;
    const current = generation.current; const request = onStart(); const account = config.email;
    const controller = new AbortController(); abort.current?.abort(); abort.current = controller;
    const timeout = setTimeout(() => controller.abort(), 300000);
    setBusy(true); setMessage('Complete Google’s account and file selection.');
    try {
      const history = await reader.current.open(controller.signal);
      if (!controller.signal.aborted && current === generation.current) {
        onOpen(history, account, request); setMessage('Selected Drive recording verified and opened.');
      }
    } catch (error) { if (current === generation.current) setMessage(error instanceof DriveReadError ? error.message : 'Unable to open the selected recording.'); }
    finally { clearTimeout(timeout); reader.current?.disconnect(); if (current === generation.current) setBusy(false); }
  }
  return <section aria-label="Open personal Drive recording">
    <button type="button" disabled={busy} onClick={() => void prepare()}>Open from personal Drive</button>
    {config && <>
      <p>Account: {config.email}<br/>Destination: {personalDestination(config)}</p>
      <p>Read-only viewer. Google’s specific-files permission also permits changes; this reader only reads the file you choose.</p>
      <button type="button" disabled={busy} onClick={() => void choose()}>Connect and choose recording</button>
    </>}
    {(config || busy) && <button type="button" onClick={() => {cancel(); onClear();}}>Disconnect and clear</button>}
    {message && <p role="status">{message}</p>}
  </section>;
}
