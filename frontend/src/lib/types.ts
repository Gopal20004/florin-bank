// Mirrors the backend's response records (backend/src/main/java/com/florin/bank/**/*Response.java).

export type Customer = { id: number; fullName: string; email: string; createdAt: string };

export type AccountType = "SAVINGS" | "CURRENT";
export type AccountStatus = "ACTIVE" | "FROZEN" | "CLOSED";

export type Account = {
  id: number;
  accountNumber: string;
  type: AccountType;
  nickname: string;
  balance: number;
  currency: string;
  status: AccountStatus;
  openedAt: string;
  closedAt: string | null;
};

export type TransactionType = "DEPOSIT" | "WITHDRAWAL" | "TRANSFER";
export type Direction = "CREDIT" | "DEBIT";

/** One side of a transaction. Own accounts carry an id and nickname; other people's are masked. */
export type Party = { accountId: number | null; name: string; accountNumber: string; own: boolean };

export type Receipt = {
  id: number;
  reference: string;
  type: TransactionType;
  amount: number;
  currency: string;
  description: string | null;
  from: Party | null;
  to: Party | null;
  createdAt: string;
};

export type StatementLine = {
  entryId: number;
  transactionId: number;
  reference: string;
  type: TransactionType;
  direction: Direction;
  amount: number;
  balanceAfter: number;
  description: string | null;
  account: { id: number; nickname: string; accountNumber: string };
  counterparty: Party | null;
  createdAt: string;
};

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export type Payee = {
  id: number;
  nickname: string;
  holderName: string;
  accountNumber: string;
  accountOpen: boolean;
  createdAt: string;
};

export type AccountLookup = { accountNumber: string; holderName: string; ownAccount: boolean; acceptsPayments: boolean };

export type Insights = {
  currency: string;
  totalBalance: number;
  monthIn: number;
  monthOut: number;
  previousMonthIn: number;
  previousMonthOut: number;
  history: { date: string; balance: number }[];
};

export type Limits = {
  perTransaction: number;
  dailyOutgoing: number;
  usedToday: number;
  remainingToday: number;
  resetsAt: string;
};

export type PublicConfig = {
  currency: string;
  zone: string;
  branchCode: string;
  perTransactionLimit: number;
  dailyOutgoingLimit: number;
  maxAccounts: number;
  maxPayees: number;
  demo: { email: string; password: string } | null;
};

export type Session = { id: number; userAgent: string | null; createdAt: string; expiresAt: string; current: boolean };
