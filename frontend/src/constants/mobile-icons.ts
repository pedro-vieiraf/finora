import type { SymbolViewProps } from 'expo-symbols';

export const mobileIcons = {
  previous: { ios: 'chevron.left', android: 'chevron_left', web: 'chevron_left' },
  next: { ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' },
  income: { ios: 'arrow.down.left', android: 'south_west', web: 'south_west' },
  expense: { ios: 'arrow.up.right', android: 'north_east', web: 'north_east' },
  transfer: { ios: 'arrow.left.arrow.right', android: 'swap_horiz', web: 'swap_horiz' },
  bank: { ios: 'building.columns', android: 'account_balance', web: 'account_balance' },
  savings: { ios: 'tray', android: 'savings', web: 'savings' },
  search: { ios: 'magnifyingglass', android: 'search', web: 'search' },
  close: { ios: 'xmark', android: 'close', web: 'close' },
} satisfies Record<string, SymbolViewProps['name']>;
