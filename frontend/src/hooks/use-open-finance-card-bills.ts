import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';

import {
  getOpenFinanceCardBills,
  type OpenFinanceCard,
} from '@/services/open-finance-card-bills-api';

export function useOpenFinanceCardBills() {
  const [cards, setCards] = useState<OpenFinanceCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useFocusEffect(
    useCallback(() => {
      let active = true;
      void getOpenFinanceCardBills().then(
        (result) => {
          if (active) {
            setCards(result);
            setError(false);
            setLoading(false);
          }
        },
        () => {
          if (active) {
            setError(true);
            setLoading(false);
          }
        },
      );
      return () => { active = false; };
    }, []),
  );

  return { cards, loading, error };
}
