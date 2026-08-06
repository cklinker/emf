import { spawn } from 'node:child_process';

/**
 * Open a URL in the OS default browser. Returns false when no opener could be
 * spawned — the caller then prints the URL for manual navigation.
 */
export function openBrowser(url: string): boolean {
  const [command, args] =
    process.platform === 'darwin'
      ? ['open', [url]]
      : process.platform === 'win32'
        ? ['cmd', ['/c', 'start', '', url.replace(/&/g, '^&')]]
        : ['xdg-open', [url]];
  try {
    const child = spawn(command, args, { detached: true, stdio: 'ignore' });
    child.on('error', () => undefined);
    child.unref();
    return true;
  } catch {
    return false;
  }
}
