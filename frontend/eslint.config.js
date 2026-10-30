import js from '@eslint/js'
import stylistic from '@stylistic/eslint-plugin'
import vue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist/', 'coverage/', 'playwright-report/', 'test-results/'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/essential'],
  {
    files: ['**/*.vue'],
    languageOptions: { parserOptions: { parser: tseslint.parser } },
  },
  {
    plugins: { '@stylistic': stylistic },
    rules: {
      'no-undef': 'off',
      curly: ['error', 'all'],
      '@stylistic/brace-style': ['error', '1tbs', { allowSingleLine: false }],
    },
  },
)
