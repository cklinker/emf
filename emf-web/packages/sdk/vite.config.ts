import { defineConfig } from 'vite';
import { resolve } from 'path';
import dts from 'vite-plugin-dts';

export default defineConfig({
  plugins: [
    dts({
      include: ['src/**/*.ts'],
      exclude: ['**/*.test.ts', '**/*.spec.ts', '**/*.property.test.ts'],
      rollupTypes: false, // Keep separate .d.ts files for multiple entry points
    }),
  ],
  build: {
    lib: {
      entry: {
        index: resolve(__dirname, 'src/index.ts'),
        'cli/index': resolve(__dirname, 'src/cli/index.ts'),
        'cli/generate-types': resolve(__dirname, 'src/cli/generate-types.ts'),
      },
      name: 'EmfSdk',
      formats: ['es', 'cjs'],
      fileName: (format, entryName) => `${entryName}.${format === 'es' ? 'js' : 'cjs'}`,
    },
    rollupOptions: {
      external: ['axios', 'zod', 'fs/promises', 'path'],
      output: {
        globals: {
          axios: 'axios',
          zod: 'zod',
        },
      },
    },
    sourcemap: true,
    minify: false,
  },
});
