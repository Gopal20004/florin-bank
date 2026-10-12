import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { useMutation } from "@tanstack/react-query";
import { ArrowDownLeft, ArrowUpRight, CaretLeft, DownloadSimple, LockOpen, PencilSimple, Prohibit, Receipt, Snowflake } from "@phosphor-icons/react";
import { api, ApiError, downloadFile, errorText } from "../lib/api";
import { useAuth, useToken } from "../lib/auth";
import { useAccounts, useActivity, useConfig, useRefreshMoney } from "../lib/queries";
import { daysAgo, isoDate, money, newIdempotencyKey } from "../lib/format";
import type { Account, Receipt as ReceiptType } from "../lib/types";
import { Button, Card, Dialog, EmptyState, ErrorNote, Field, Page, Skeleton } from "../components/ui";
import { AccountCard } from "../components/AccountCard";
import { ActivityList } from "../components/ActivityList";
import { CopyNumber } from "./AccountsPage";
import { useToast } from "../components/toast";

type CashKind = "deposits" | "withdrawals";
const AMOUNT = /^\d{1,13}(\.\d{1,2})?$/;

export default function AccountPage() {
  const { id } = useParams();
  const accountId = Number(id);
  const token = useToken();
  const { customer } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const refresh = useRefreshMoney();
  const accounts = useAccounts();
  const config = useConfig();
  const [page, setPage] = useState(0);
  const activity = useActivity({ accountId, page, size: 15 });
  const account = accounts.data?.find((candidate) => candidate.id === accountId);

  const [cash, setCash] = useState<CashKind | null>(null);
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [cashError, setCashError] = useState<string | null>(null);
  const [cashKey, setCashKey] = useState(newIdempotencyKey);
  const [renaming, setRenaming] = useState(false);
  const [nickname, setNickname] = useState("");
  const [closing, setClosing] = useState(false);
  const [statement, setStatement] = useState(false);
  const [from, setFrom] = useState(() => isoDate(daysAgo(30)));
  const [to, setTo] = useState(() => isoDate(new Date()));
  const [downloading, setDownloading] = useState(false);
  const [params, setParams] = useSearchParams();

  // Arriving from "Add money" or "Withdraw" on the overview opens that dialog straight away, once.
  useEffect(() => {
    const action = params.get("action");
    if (!account || !action) return;
    if (action === "deposit" && account.status !== "CLOSED") setCash("deposits");
    if (action === "withdraw" && account.status === "ACTIVE") setCash("withdrawals");
    setParams({}, { replace: true });
  }, [account, params, setParams]);

  const move = useMutation({
    mutationFn: ({ kind, value }: { kind: CashKind; value: string }) =>
      api<ReceiptType>(`/api/accounts/${accountId}/${kind}`, { method: "POST", token, idempotencyKey: cashKey, json: { amount: value, description: note.trim() || null } }),
    onSuccess: (receipt, variables) => {
      void refresh();
      setCash(null);
      setAmount("");
      setNote("");
      setCashKey(newIdempotencyKey());
      toast.show(`${variables.kind === "deposits" ? "Deposited" : "Withdrew"} ${money(receipt.amount)}`, "success");
    },
    onError: (reason) => setCashError(reason instanceof ApiError && reason.fields.amount ? reason.fields.amount : errorText(reason))
  });

  const change = useMutation({
    mutationFn: ({ action, body }: { action: "freeze" | "unfreeze" | "close" | "rename"; body?: unknown }) =>
      action === "rename"
        ? api<Account>(`/api/accounts/${accountId}`, { method: "PATCH", token, json: body })
        : api<Account>(`/api/accounts/${accountId}/${action}`, { method: "POST", token }),
    onSuccess: (updated, variables) => {
      void refresh();
      setRenaming(false);
      setClosing(false);
      toast.show(variables.action === "rename" ? `Renamed to ${updated.nickname}` : variables.action === "freeze" ? "Account frozen" : variables.action === "unfreeze" ? "Account active again" : "Account closed", "success");
    },
    onError: (reason) => toast.show(errorText(reason), "error")
  });

  function submitCash(event: FormEvent) {
    event.preventDefault();
    if (!cash) return;
    const value = amount.trim();
    if (!AMOUNT.test(value) || Number(value) <= 0) {
      setCashError("Enter an amount like 2500 or 999.50");
      return;
    }
    setCashError(null);
    move.mutate({ kind: cash, value });
  }

  async function download() {
    setDownloading(true);
    try {
      await downloadFile(`/api/accounts/${accountId}/statement?from=${from}&to=${to}`, token, `florin-statement-${from}-to-${to}.csv`);
      setStatement(false);
      toast.show("Statement downloaded", "success");
    } catch (reason) {
      toast.show(errorText(reason), "error");
    } finally {
      setDownloading(false);
    }
  }

  if (accounts.isError) return <Page title="Account"><ErrorNote text={errorText(accounts.error)} retry={() => void accounts.refetch()} /></Page>;
  if (accounts.data && !account) {
    return <Page title="Account not found"><EmptyState icon={<Receipt size={24} />} title="No such account" text="It may belong to someone else, or the link is old." action={<Link to="/accounts" className="btn btn-secondary btn-md"><span>Back to accounts</span></Link>} /></Page>;
  }
  if (!account) return <Page title="Account"><Skeleton height={300} radius={20} /></Page>;

  const closed = account.status === "CLOSED";
  const frozen = account.status === "FROZEN";

  return (
    <Page title={account.nickname} lead={account.type === "SAVINGS" ? "Savings account" : "Current account"}
      actions={<Link to="/accounts" className="btn btn-ghost btn-md"><CaretLeft size={16} /><span>All accounts</span></Link>}>
      <div className="detail-grid">
        <div className="detail-side">
          <AccountCard account={account} holder={customer?.fullName ?? ""} branchCode={config.data?.branchCode} />
          <Card className="card-tight">
            <dl className="kv">
              <dt>Account number</dt><dd><CopyNumber accountNumber={account.accountNumber} /></dd>
              <dt>Branch</dt><dd className="mono">{config.data?.branchCode ?? "Florin"}</dd>
              <dt>Status</dt><dd>{closed ? "Closed" : frozen ? "Frozen" : "Active"}</dd>
              <dt>Balance</dt><dd>{money(account.balance)}</dd>
            </dl>
          </Card>
          <Card className="card-tight">
            <div className="action-list">
              <button className="action-row" disabled={closed} onClick={() => { setCash("deposits"); setCashError(null); }}><ArrowDownLeft size={20} weight="bold" /><div><strong>Deposit cash</strong><span>Add money to this account</span></div></button>
              <button className="action-row" disabled={closed || frozen} onClick={() => { setCash("withdrawals"); setCashError(null); }}><ArrowUpRight size={20} weight="bold" /><div><strong>Withdraw cash</strong><span>{frozen ? "Unfreeze the account first" : "Take money out"}</span></div></button>
              <button className="action-row" disabled={closed} onClick={() => navigate(`/pay?from=${account.id}`)}><ArrowUpRight size={20} weight="bold" /><div><strong>Pay from here</strong><span>To a payee or another account</span></div></button>
              <button className="action-row" onClick={() => setStatement(true)}><DownloadSimple size={20} weight="bold" /><div><strong>Download statement</strong><span>CSV for any date range</span></div></button>
              <button className="action-row" disabled={closed} onClick={() => { setNickname(account.nickname); setRenaming(true); }}><PencilSimple size={20} weight="bold" /><div><strong>Rename</strong><span>Call it what it is for</span></div></button>
              {frozen ? (
                <button className="action-row" onClick={() => change.mutate({ action: "unfreeze" })}><LockOpen size={20} weight="bold" /><div><strong>Unfreeze</strong><span>Allow money out again</span></div></button>
              ) : (
                <button className="action-row" disabled={closed} onClick={() => change.mutate({ action: "freeze" })}><Snowflake size={20} weight="bold" /><div><strong>Freeze</strong><span>Money can come in, not go out</span></div></button>
              )}
              <button className="action-row danger" disabled={closed} onClick={() => setClosing(true)}><Prohibit size={20} weight="bold" /><div><strong>Close account</strong><span>Only once the balance is zero</span></div></button>
            </div>
          </Card>
        </div>

        <Card>
          <div className="card-head"><h2>Transactions</h2>{activity.data ? <span className="muted" style={{ fontSize: "0.86rem" }}>{activity.data.totalElements} in total</span> : null}</div>
          {activity.isError ? <ErrorNote text={errorText(activity.error)} retry={() => void activity.refetch()} /> : null}
          {activity.data ? (
            activity.data.content.length === 0 ? <EmptyState icon={<Receipt size={24} />} title="No transactions yet" text="Deposit some cash or receive a payment to see it here." /> : (
              <div style={{ opacity: activity.isFetching ? 0.6 : 1, transition: "opacity 200ms" }}>
                <ActivityList lines={activity.data.content} showAccount={false} showBalance />
                {activity.data.totalPages > 1 ? (
                  <div className="pager">
                    <span>Page {page + 1} of {activity.data.totalPages}</span>
                    <div className="pager-buttons">
                      <Button size="sm" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Newer</Button>
                      <Button size="sm" disabled={page + 1 >= activity.data.totalPages} onClick={() => setPage((value) => value + 1)}>Older</Button>
                    </div>
                  </div>
                ) : null}
              </div>
            )
          ) : !activity.isError ? <div className="stat-stack"><Skeleton height={54} /><Skeleton height={54} /><Skeleton height={54} /></div> : null}
        </Card>
      </div>

      <Dialog open={cash !== null} onClose={() => setCash(null)} title={cash === "deposits" ? "Deposit cash" : "Withdraw cash"}>
        <form className="form-grid" onSubmit={submitCash} noValidate>
          <Field className="amount-field" label="Amount" value={amount} onChange={(event) => setAmount(event.target.value)} inputMode="decimal" placeholder="0.00" prefix="₹" error={cashError} autoComplete="off" />
          <Field label="Note (optional)" value={note} onChange={(event) => setNote(event.target.value)} maxLength={140} placeholder={cash === "deposits" ? "Salary" : "Groceries"} />
          {cash === "withdrawals" ? <p className="muted" style={{ fontSize: "0.84rem" }}>Available: {money(account.balance)}. Withdrawals count toward your daily limit.</p> : null}
          <div className="form-actions">
            <Button type="button" variant="ghost" onClick={() => setCash(null)}>Cancel</Button>
            <Button type="submit" variant="primary" loading={move.isPending}>{cash === "deposits" ? "Deposit" : "Withdraw"}</Button>
          </div>
        </form>
      </Dialog>

      <Dialog open={renaming} onClose={() => setRenaming(false)} title="Rename account">
        <form className="form-grid" onSubmit={(event) => { event.preventDefault(); change.mutate({ action: "rename", body: { nickname } }); }} noValidate>
          <Field label="Name" value={nickname} onChange={(event) => setNickname(event.target.value)} maxLength={40} />
          <div className="form-actions">
            <Button type="button" variant="ghost" onClick={() => setRenaming(false)}>Cancel</Button>
            <Button type="submit" variant="primary" loading={change.isPending}>Save</Button>
          </div>
        </form>
      </Dialog>

      <Dialog open={closing} onClose={() => setClosing(false)} title="Close this account?">
        <p style={{ color: "var(--ink-2)" }}>Closing keeps its history but stops all money in and out. {account.balance > 0 ? `Move the ${money(account.balance)} it holds to another account first.` : "The balance is zero, so it can be closed now."}</p>
        <div className="form-actions">
          <Button variant="ghost" onClick={() => setClosing(false)}>Keep it open</Button>
          <Button variant="danger" loading={change.isPending} disabled={account.balance > 0} onClick={() => change.mutate({ action: "close" })}>Close account</Button>
        </div>
      </Dialog>

      <Dialog open={statement} onClose={() => setStatement(false)} title="Download statement">
        <div className="form-grid">
          <div className="form-row">
            <Field label="From" type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} />
            <Field label="To" type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} />
          </div>
          <p className="muted" style={{ fontSize: "0.84rem" }}>A CSV with date, reference, description, counterparty, debit, credit and running balance. Up to one year at a time.</p>
          <div className="form-actions">
            <Button variant="ghost" onClick={() => setStatement(false)}>Cancel</Button>
            <Button variant="primary" loading={downloading} onClick={() => void download()} icon={<DownloadSimple size={16} weight="bold" />}>Download CSV</Button>
          </div>
        </div>
      </Dialog>
    </Page>
  );
}
