import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { motion } from "motion/react";
import { useMutation } from "@tanstack/react-query";
import { Copy, Plus, Vault } from "@phosphor-icons/react";
import { api, ApiError, errorText } from "../lib/api";
import { useToken } from "../lib/auth";
import { useAccounts, useConfig, useRefreshMoney } from "../lib/queries";
import { groupDigits, money } from "../lib/format";
import type { Account, AccountType } from "../lib/types";
import { Button, Card, Dialog, EmptyState, ErrorNote, Field, Page, Pill, Skeleton, stagger } from "../components/ui";
import { useToast } from "../components/toast";

function statusTone(status: Account["status"]): "good" | "warn" | "neutral" {
  return status === "ACTIVE" ? "good" : status === "FROZEN" ? "warn" : "neutral";
}

export function CopyNumber({ accountNumber }: { accountNumber: string }) {
  const toast = useToast();
  return (
    <span className="account-number">
      {groupDigits(accountNumber)}
      <button className="icon-btn" style={{ width: 28, height: 28 }} aria-label="Copy account number" onClick={() => {
        void navigator.clipboard?.writeText(accountNumber).then(() => toast.show("Account number copied", "success"), () => toast.show("Could not copy", "error"));
      }}><Copy size={15} /></button>
    </span>
  );
}

export default function AccountsPage() {
  const token = useToken();
  const toast = useToast();
  const refresh = useRefreshMoney();
  const accounts = useAccounts();
  const config = useConfig();
  const [opening, setOpening] = useState(false);
  const [type, setType] = useState<AccountType>("SAVINGS");
  const [nickname, setNickname] = useState("");
  const [error, setError] = useState<string | null>(null);

  const open = useMutation({
    mutationFn: () => api<Account>("/api/accounts", { method: "POST", token, json: { type, nickname: nickname.trim() || null } }),
    onSuccess: (account) => {
      void refresh();
      setOpening(false);
      setNickname("");
      toast.show(`${account.nickname} is open`, "success");
    },
    onError: (reason) => setError(reason instanceof ApiError && reason.fields.nickname ? reason.fields.nickname : errorText(reason))
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    open.mutate();
  }

  const openCount = accounts.data?.filter((account) => account.status !== "CLOSED").length ?? 0;
  const max = config.data?.maxAccounts ?? 5;

  return (
    <Page title="Accounts" lead={`${openCount} of ${max} accounts open. Savings for keeping, current for business.`}
      actions={<Button variant="primary" icon={<Plus size={16} weight="bold" />} onClick={() => setOpening(true)} disabled={openCount >= max}>Open account</Button>}>
      {accounts.isError ? <ErrorNote text={errorText(accounts.error)} retry={() => void accounts.refetch()} /> : null}
      {accounts.data ? (
        accounts.data.length === 0 ? <EmptyState icon={<Vault size={24} />} title="No accounts" text="Open your first account to get started." /> : (
          <motion.div className="account-grid" variants={stagger.container} initial="hidden" animate="show">
            {accounts.data.map((account) => (
              <motion.div key={account.id} variants={stagger.item}>
                <Card className="account-tile-card">
                  <div className="account-tile">
                    <div className="account-tile-head">
                      <strong>{account.nickname}</strong>
                      <Pill tone={statusTone(account.status)}>{account.status === "ACTIVE" ? "Active" : account.status === "FROZEN" ? "Frozen" : "Closed"}</Pill>
                    </div>
                    <CopyNumber accountNumber={account.accountNumber} />
                    <div className="account-balance">{money(account.balance)}</div>
                    <div className="muted" style={{ fontSize: "0.82rem" }}>{account.type === "SAVINGS" ? "Savings account" : "Current account"}</div>
                    <Link to={`/accounts/${account.id}`} className="btn btn-secondary btn-sm"><span>Open</span></Link>
                  </div>
                </Card>
              </motion.div>
            ))}
          </motion.div>
        )
      ) : <div className="account-grid"><Skeleton height={200} radius={20} /><Skeleton height={200} radius={20} /></div>}

      <Dialog open={opening} onClose={() => setOpening(false)} title="Open a new account">
        <form className="form-grid" onSubmit={submit} noValidate>
          <div className="field">
            <label htmlFor="account-type">Account type</label>
            <select id="account-type" className="select" value={type} onChange={(event) => setType(event.target.value as AccountType)}>
              <option value="SAVINGS">Savings</option>
              <option value="CURRENT">Current</option>
            </select>
          </div>
          <Field label="Name (optional)" value={nickname} onChange={(event) => setNickname(event.target.value)} placeholder={type === "SAVINGS" ? "Rainy Day Fund" : "Studio"} maxLength={40} error={error} />
          <div className="form-actions">
            <Button type="button" variant="ghost" onClick={() => setOpening(false)}>Cancel</Button>
            <Button type="submit" variant="primary" loading={open.isPending}>Open account</Button>
          </div>
        </form>
      </Dialog>
    </Page>
  );
}
