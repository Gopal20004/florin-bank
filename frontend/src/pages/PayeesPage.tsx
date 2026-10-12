import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router";
import { motion } from "motion/react";
import { useMutation } from "@tanstack/react-query";
import { PaperPlaneTilt, PencilSimple, Plus, ShieldCheck, Trash, UsersThree } from "@phosphor-icons/react";
import { api, ApiError, errorText } from "../lib/api";
import { useToken } from "../lib/auth";
import { useConfig, usePayees, useRefreshMoney } from "../lib/queries";
import { groupDigits } from "../lib/format";
import type { AccountLookup, Payee } from "../lib/types";
import { Avatar, Button, Card, Dialog, EmptyState, ErrorNote, Field, Page, Pill, Skeleton, stagger } from "../components/ui";
import { useToast } from "../components/toast";

export default function PayeesPage() {
  const token = useToken();
  const toast = useToast();
  const refresh = useRefreshMoney();
  const payees = usePayees();
  const config = useConfig();
  const [adding, setAdding] = useState(false);
  const [number, setNumber] = useState("");
  const [nickname, setNickname] = useState("");
  const [lookup, setLookup] = useState<{ status: "idle" | "busy" | "found" | "error"; result?: AccountLookup; message?: string }>({ status: "idle" });
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<Payee | null>(null);
  const [editName, setEditName] = useState("");
  const [removing, setRemoving] = useState<Payee | null>(null);

  const digits = number.replace(/[\s-]/g, "");
  useEffect(() => {
    if (!adding || digits.length !== 12) {
      setLookup({ status: "idle" });
      return;
    }
    const controller = new AbortController();
    setLookup({ status: "busy" });
    api<AccountLookup>("/api/payees/lookup", { method: "POST", token, json: { accountNumber: digits }, signal: controller.signal })
      .then((result) => setLookup({ status: "found", result }))
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setLookup({ status: "error", message: errorText(reason) });
      });
    return () => controller.abort();
  }, [adding, digits, token]);

  const add = useMutation({
    mutationFn: () => api<Payee>("/api/payees", { method: "POST", token, json: { accountNumber: digits, nickname: nickname.trim() || null } }),
    onSuccess: (payee) => {
      void refresh();
      setAdding(false);
      setNumber("");
      setNickname("");
      toast.show(`${payee.nickname} saved`, "success");
    },
    onError: (reason) => setError(reason instanceof ApiError && reason.fields.nickname ? reason.fields.nickname : errorText(reason))
  });

  const rename = useMutation({
    mutationFn: (payee: Payee) => api<Payee>(`/api/payees/${payee.id}`, { method: "PATCH", token, json: { nickname: editName } }),
    onSuccess: () => { void refresh(); setEditing(null); toast.show("Payee renamed", "success"); },
    onError: (reason) => toast.show(errorText(reason), "error")
  });

  const remove = useMutation({
    mutationFn: (payee: Payee) => api<void>(`/api/payees/${payee.id}`, { method: "DELETE", token }),
    onSuccess: () => { void refresh(); setRemoving(null); toast.show("Payee removed", "success"); },
    onError: (reason) => toast.show(errorText(reason), "error")
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    add.mutate();
  }

  const canSave = lookup.status === "found" && lookup.result?.acceptsPayments && !lookup.result.ownAccount;
  const max = config.data?.maxPayees ?? 50;

  return (
    <Page title="Payees" lead="People and businesses you pay often. We confirm the account holder's name before anything is saved."
      actions={<Button variant="primary" icon={<Plus size={16} weight="bold" />} onClick={() => { setAdding(true); setError(null); }} disabled={(payees.data?.length ?? 0) >= max}>Add payee</Button>}>
      {payees.isError ? <ErrorNote text={errorText(payees.error)} retry={() => void payees.refetch()} /> : null}
      <Card>
        {payees.data ? (
          payees.data.length === 0 ? (
            <EmptyState icon={<UsersThree size={24} />} title="No payees yet" text="Save someone once and paying them takes two taps." action={<Button variant="primary" onClick={() => setAdding(true)}>Add your first payee</Button>} />
          ) : (
            <motion.div className="payee-list" variants={stagger.container} initial="hidden" animate="show">
              {payees.data.map((payee) => (
                <motion.div key={payee.id} className="payee-row" variants={stagger.item}>
                  <Avatar name={payee.holderName} size={40} />
                  <div className="payee-main">
                    <strong>{payee.nickname} {!payee.accountOpen ? <Pill tone="neutral">Account closed</Pill> : null}</strong>
                    <span>{payee.holderName} · <span className="mono">{groupDigits(payee.accountNumber)}</span></span>
                  </div>
                  <div className="payee-actions">
                    <Link to={`/pay?to=${payee.accountNumber}`} className={`btn btn-primary btn-sm ${!payee.accountOpen ? "disabled" : ""}`} aria-disabled={!payee.accountOpen} onClick={(event) => { if (!payee.accountOpen) event.preventDefault(); }}><PaperPlaneTilt size={15} weight="bold" /><span>Pay</span></Link>
                    <button className="icon-btn" aria-label={`Rename ${payee.nickname}`} onClick={() => { setEditing(payee); setEditName(payee.nickname); }}><PencilSimple size={17} /></button>
                    <button className="icon-btn" aria-label={`Remove ${payee.nickname}`} onClick={() => setRemoving(payee)}><Trash size={17} /></button>
                  </div>
                </motion.div>
              ))}
            </motion.div>
          )
        ) : <div className="stat-stack"><Skeleton height={56} /><Skeleton height={56} /><Skeleton height={56} /></div>}
      </Card>

      <Dialog open={adding} onClose={() => setAdding(false)} title="Add a payee">
        <form className="form-grid" onSubmit={submit} noValidate>
          <Field label="Account number" mono value={number} onChange={(event) => setNumber(event.target.value)} inputMode="numeric" placeholder="12 digits" autoComplete="off" hint="We check the number and show you the holder's name first." />
          {lookup.status === "busy" ? <Skeleton height={48} radius={14} /> : null}
          {lookup.status === "error" ? <div className="form-error">{lookup.message}</div> : null}
          {lookup.status === "found" && lookup.result ? (
            <div className={`lookup-result ${canSave ? "" : "warn"}`}>
              <ShieldCheck size={22} weight="fill" />
              <div>
                <strong>{lookup.result.holderName}</strong>
                <div className="muted" style={{ fontSize: "0.82rem" }}>{lookup.result.ownAccount ? "This is one of your own accounts" : !lookup.result.acceptsPayments ? "This account is closed" : "Account holder confirmed"}</div>
              </div>
            </div>
          ) : null}
          <Field label="Save as (optional)" value={nickname} onChange={(event) => setNickname(event.target.value)} maxLength={40} placeholder={lookup.result?.holderName ?? "Landlord"} error={error} />
          <div className="form-actions">
            <Button type="button" variant="ghost" onClick={() => setAdding(false)}>Cancel</Button>
            <Button type="submit" variant="primary" loading={add.isPending} disabled={!canSave}>Save payee</Button>
          </div>
        </form>
      </Dialog>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} title="Rename payee">
        <form className="form-grid" onSubmit={(event) => { event.preventDefault(); if (editing) rename.mutate(editing); }} noValidate>
          <Field label="Name" value={editName} onChange={(event) => setEditName(event.target.value)} maxLength={40} />
          <div className="form-actions">
            <Button type="button" variant="ghost" onClick={() => setEditing(null)}>Cancel</Button>
            <Button type="submit" variant="primary" loading={rename.isPending}>Save</Button>
          </div>
        </form>
      </Dialog>

      <Dialog open={removing !== null} onClose={() => setRemoving(null)} title={`Remove ${removing?.nickname ?? "payee"}?`}>
        <p style={{ color: "var(--ink-2)" }}>You can always add them again by account number. Past payments stay in your activity.</p>
        <div className="form-actions">
          <Button variant="ghost" onClick={() => setRemoving(null)}>Keep</Button>
          <Button variant="danger" loading={remove.isPending} onClick={() => removing && remove.mutate(removing)}>Remove</Button>
        </div>
      </Dialog>
    </Page>
  );
}
