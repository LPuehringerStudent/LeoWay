import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const frontend = resolve(root, 'frontend');
const backend = resolve(root, 'backend');
const children = new Set();
let interrupted = false;

function stopChildren() {
  for (const child of children) {
    child.kill();
  }
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    interrupted = true;
    stopChildren();
  });
}

function run(command, args, { cwd = root, env = process.env, capture = false } = {}) {
  if (interrupted) return Promise.resolve();

  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      env,
      stdio: capture ? ['ignore', 'pipe', 'inherit'] : 'inherit',
    });
    children.add(child);

    let output = '';
    if (capture) {
      child.stdout.setEncoding('utf8').on('data', (chunk) => { output += chunk; });
    }

    child.once('error', reject);
    child.once('close', (code, signal) => {
      children.delete(child);
      if (code === 0 || interrupted) {
        resolve(output);
      } else {
        reject(new Error(`${command} ${args.join(' ')} exited with ${signal ?? code}.`));
      }
    });
  });
}

async function start() {
  if (process.versions.node.split('.')[0] !== '26') {
    throw new Error('LeoWay requires Node.js 26. See .nvmrc.');
  }

  const envFile = resolve(root, '.env');
  if (!existsSync(envFile)) {
    writeFileSync(envFile, `LEOWAY_DB_PASSWORD=${randomBytes(24).toString('hex')}\n`, {
      flag: 'wx',
      mode: 0o600,
    });
  }

  // Read Compose's resolved password so both processes use the same value.
  const config = await run('docker', ['compose', 'config', '--format', 'json'], { capture: true });
  if (interrupted) return;
  const password = JSON.parse(config).services.db.environment.POSTGRES_PASSWORD;
  const apiEnv = {
    ...process.env,
    QUARKUS_DATASOURCE_PASSWORD: password,
    QUARKUS_PROFILE: 'local',
  };

  await run('docker', ['compose', 'up', '-d', '--wait', 'db']);
  await run(process.execPath, [process.env.npm_execpath, 'ci'], { cwd: frontend });
  const mavenArgs = ['-B', '-ntp', 'package', '-DskipTests'];
  if (process.platform === 'win32') {
    await run('cmd.exe', ['/d', '/c', 'mvn.cmd', ...mavenArgs], { cwd: backend });
  } else {
    await run('mvn', mavenArgs, { cwd: backend });
  }
  if (interrupted) return;

  await Promise.race([
    run('java', ['-jar', 'target/quarkus-app/quarkus-run.jar'], { cwd: backend, env: apiEnv }),
    run(process.execPath, ['node_modules/@angular/cli/bin/ng.js', 'serve'], { cwd: frontend }),
  ]);
}

try {
  await start();
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  stopChildren();
}
