import { spawn } from 'node:child_process';
import net from 'node:net';

function isPortAvailable(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.unref();
    server.once('error', () => resolve(false));
    server.listen(port, () => {
      server.close(() => resolve(true));
    });
  });
}

async function findAvailablePort(startPort) {
  for (let port = startPort; port < startPort + 20; port += 1) {
    if (await isPortAvailable(port)) {
      return port;
    }
  }

  throw new Error(`No available Expo port found starting from ${startPort}`);
}

const requestedPort = Number.parseInt(process.env.EXPO_PORT ?? '8081', 10);
const port = Number.isNaN(requestedPort) ? 8081 : await findAvailablePort(requestedPort);

if (port !== requestedPort) {
  console.log(`[expo] Port ${requestedPort} is busy, using port ${port} instead`);
}

const child = spawn(
  'npx',
  ['expo', 'start', '--web', '--port', String(port)],
  {
    stdio: 'inherit',
    shell: process.platform === 'win32',
    env: {
      ...process.env,
      EXPO_USE_METRO_WORKSPACE_ROOT: '1',
      EXPO_PORT: String(port),
    },
  },
);

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }

  process.exit(code ?? 0);
});
