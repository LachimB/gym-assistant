// Loopback-only static reader and private configuration/budget. Never receives Drive tokens or session bytes.
import {createServer} from 'node:http';
import {readFileSync,writeFileSync,renameSync,realpathSync,statSync,openSync,fsyncSync,closeSync} from 'node:fs';
import {resolve,sep,extname,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

export function consumeBudget(ledger, allocationId, kind) {
  if (!ledger || ledger.version !== 1 || ledger.reservationVerified !== true || ledger.allocationId !== allocationId ||
      !Number.isSafeInteger(ledger.globalAllocated) || ledger.globalAllocated < 30 || ledger.globalAllocated > 500 ||
      !['request','authorization','picker'].includes(kind)) throw new Error('Unavailable allowance');
  const caps = {request:30,authorization:4,picker:2};
  for (const key of Object.keys(caps)) if (!Number.isSafeInteger(ledger.used?.[key]) || ledger.used[key] < 0 || ledger.used[key] > caps[key]) throw new Error('Invalid allowance');
  if (ledger.used[kind] >= caps[kind]) throw new Error('Allowance exhausted');
  return {...ledger,used:{...ledger.used,[kind]:ledger.used[kind]+1}};
}
export function historyServer({root,configPath,ledgerPath,port=4173}) {
  const publicRoot = realpathSync(root);
  function privateConfig() {
    if (!configPath || !ledgerPath) throw new Error('Disabled');
    const config=JSON.parse(readFileSync(configPath,'utf8'));
    const ledger=JSON.parse(readFileSync(ledgerPath,'utf8'));
    // Validation is also performed in the browser. Check the durable reservation before exposing live config.
    if (config.allocationId !== ledger.allocationId || !ledger.reservationVerified || ledger.version !== 1) throw new Error('Unreserved');
    if (Object.keys(config).sort().join(',') !== 'allocationId,apiKey,appId,clientId,email,folderId,marker,permissionId') throw new Error('Unexpected configuration');
    return config;
  }
  return createServer(async (request,response) => {
    response.setHeader('Cache-Control','no-store'); response.setHeader('X-Content-Type-Options','nosniff');
    response.setHeader('Referrer-Policy','strict-origin-when-cross-origin'); response.setHeader('Cross-Origin-Resource-Policy','same-origin');
    const hosts=[`127.0.0.1:${port}`,`localhost:${port}`];
    const origin=request.headers.origin;
    const allowed=hosts.includes(request.headers.host ?? '') && (!origin || hosts.map(h=>'http://'+h).includes(origin)) &&
      (!request.headers['sec-fetch-site'] || ['same-origin','none'].includes(request.headers['sec-fetch-site']));
    if (!allowed) {response.writeHead(403);response.end('Local access only.');return;}
    try {
      const path=new URL(request.url,'http://127.0.0.1').pathname;
      if (path === '/__private/drive-reader/config' && request.method === 'GET') {
        const config=privateConfig();response.setHeader('Content-Type','application/json');response.end(JSON.stringify(config));return;
      }
      if (path === '/__private/drive-reader/budget' && request.method === 'POST') {
        if (!origin || request.headers['x-gym-local-reader'] !== '1' || request.headers['content-type'] !== 'application/json') throw new Error('Rejected');
        let text='';for await (const chunk of request) {text+=chunk.toString();if (text.length>512) throw new Error('Rejected');}
        const body=JSON.parse(text); const config=privateConfig();
        if (Object.keys(body).sort().join(',')!=='allocationId,kind' || body.allocationId!==config.allocationId) throw new Error('Rejected');
        const ledger=consumeBudget(JSON.parse(readFileSync(ledgerPath,'utf8')),body.allocationId,body.kind);
        // Synchronous read/replace serializes counter transitions, before granting permission to transmit.
        const staged=ledgerPath+'.stage';const fd=openSync(staged,'w',0o600);
        try {writeFileSync(fd,JSON.stringify(ledger)+'\n');fsyncSync(fd);} finally {closeSync(fd);}
        renameSync(staged,ledgerPath);const directory=openSync(dirname(ledgerPath),'r');try {fsyncSync(directory);} finally {closeSync(directory);}
        response.writeHead(204);response.end();return;
      }
      if (request.method !== 'GET' || path.startsWith('/__private/')) throw new Error('Rejected');
      const target=realpathSync(resolve(publicRoot,'.'+decodeURIComponent(path==='/'?'/index.html':path)));
      if (!target.startsWith(publicRoot+sep) || !statSync(target).isFile() || !['.html','.js','.css'].includes(extname(target))) throw new Error('Rejected');
      const content=readFileSync(target);response.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8'})[extname(target)]);response.end(content);
    } catch {response.writeHead(403);response.end('Local reader configuration or allowance unavailable.');}
  });
}
if (process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const repo=resolve(dirname(fileURLToPath(import.meta.url)),'..');
  const server=historyServer({root:resolve(repo,'apps/web/dist'),configPath:process.env.GYM_PERSONAL_DRIVE_CONFIG,ledgerPath:process.env.GYM_PERSONAL_DRIVE_LEDGER});
  server.listen(4173,'127.0.0.1',()=>process.stdout.write('Local web history reader ready on loopback port4173.\n'));
  server.on('error',()=>{process.stderr.write('Local reader could not start; existing processes were not changed.\n');process.exitCode=1;});
}
