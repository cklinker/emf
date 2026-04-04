/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,svelte,ts,tsx,vue}'],
  theme: {
    extend: {
      colors: {
        kelta: {
          50: '#F8FAFC',   // Slate 50 — off-white BG
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#06B6D4',   // Cyan — primary accent
          600: '#3B82F6',   // Blue — secondary accent
          700: '#334155',   // Slate 700 — secondary text
          800: '#1e293b',
          900: '#0F172A',   // Navy — primary dark
          950: '#020617',
        },
        navy: '#0F172A',
        cyan: '#06B6D4',
        blue: '#3B82F6',
        success: '#10B981',
        warning: '#F59E0B',
        error: '#EF4444',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
    },
  },
  plugins: [],
};
