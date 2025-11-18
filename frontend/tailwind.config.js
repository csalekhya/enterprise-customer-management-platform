/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // One brand ramp, used for primary actions and active navigation only, so that
        // colour in the data tables always means data and never decoration.
        brand: {
          50: '#eef4ff',
          100: '#d9e5ff',
          200: '#bcd0ff',
          300: '#8eb0ff',
          400: '#5985ff',
          500: '#325dff',
          600: '#1d3ff5',
          700: '#172fe1',
          800: '#1829b6',
          900: '#1a298f',
        },
        ink: {
          700: '#1f2937',
          800: '#111827',
          900: '#0b1220',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'Helvetica', 'Arial', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      boxShadow: {
        card: '0 1px 2px 0 rgb(16 24 40 / 0.06), 0 1px 3px 0 rgb(16 24 40 / 0.10)',
        drawer: '-8px 0 32px -8px rgb(16 24 40 / 0.20)',
      },
      keyframes: {
        'slide-in': {
          '0%': { transform: 'translateX(16px)', opacity: '0' },
          '100%': { transform: 'translateX(0)', opacity: '1' },
        },
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
      },
      animation: {
        'slide-in': 'slide-in 180ms ease-out',
        'fade-in': 'fade-in 140ms ease-out',
      },
    },
  },
  plugins: [],
};
