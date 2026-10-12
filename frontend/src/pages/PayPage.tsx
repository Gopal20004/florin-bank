import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { useMutation } from "@tanstack/react-query";
import { ArrowsLeftRight, Copy, MagnifyingGlass, ShieldCheck } from "@phosphor-icons/react";
import { api, ApiError, errorText, request } from "../lib/api";
import { useToken } from "../lib/auth";
import { useAccounts, useLimits, usePayees, useRefreshMoney } from "../lib/queries";
import { dateTimeLabel, groupDigits, money, newIdempotencyKey } from "../lib/format";
import type { AccountLookup, Receipt } from "../lib/types";
import { Avatar, Button, Card, Field, Page, Skeleton } from "../components/ui";
import { Money } from "../components/Money";
import { useToast } from "../components/toast";

type Step = "who" | "amount" | "review" | "done";
type Target = { accountNumber: string; name: string; own: boolean };
const AMOUNT = /^\d{1,13}(\.\d{1,2})?$/;
const QUICK = [500, 1000, 2000, 5000];
const slide = {
  initial: { opacity: 0, x: 28, filter: "blur(6px)" },
  animate: { opacity: 1, x: 0, filter: "blur(0px)" },
  exit: { opacity: 0, x: -28, filter: "blur(6px)" },
  transition: { duration: 0.42, ease: [0.32, 0.72, 0, 1] as const }
};

/** Gold dots flung outward from the check mark, then fading: the coin's dots, celebrating. */
function Burst() {
  return (
    <div className="burst" aria-hidden="true">
      {Array.from({ length: 28 }, (_, index) => (
        <span key={index} className="burst-arm" style={{ transform: `rotate(${index * (360 / 28)}deg)` }}>
          <motion.span
            className="burst-dot"
            initial={{ x: 0, opacity: 0, scale: 0.4 }}
            animate={{ x: 80 + (index % 3) * 22, opacity: [0, 1, 0], scale: [0.4, 1, 0.3] }}
            transition={{ duration: 1.1, delay: 0.35 + (index % 4) * 0.04, ease: [0.22, 1, 0.36, 1] }}
          />
        </span>
      ))}
    </div>
  );
}

export default function PayPage() {
  const token = useToken();
  const toast = useToast();
  const refresh = useRefreshMoney();
  const [params] = useSearchParams();
  const accounts = useAccounts();
  const payees = usePayees();
  const limits = useLimits();

  const [step, setStep] = useState<Step>("who");
  const [target, setTarget] = useState<Target | null>(null);
  const [typed, setTyped] = useState("");
  const [lookup, setLookup] = useState<{ status: "idle" | "busy" | "found" | "error"; result?: AccountLookup; message?: string }>({ status: "idle" });
  const [fromId, setFromId] = useState<number | null>(params.get("from") ? Number(params.get("from")) : null);
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [amountError, setAmountError] = useState<string | null>(null);
  const [key, setKey] = useState(newIdempotencyKey);
  const [done, setDone] = useState<{ receipt: Receipt; replayed: boolean } | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const open = useMemo(() => (accounts.data ?? []).filter((account) => account.status === "ACTIVE"), [accounts.data]);
  const sources = target ? open.filter((account) => account.accountNumber !== target.accountNumber) : open;
  const from = sources.find((account) => account.id === fromId) ?? sources[0] ?? null;
  const ownMode = params.get("mode") === "own";

  // Arrived with ?to=: a saved payee goes straight to the amount; any other number (a scanned QR code, a
  // shared link) is filled in and looked up, so the holder's name is confirmed before anything is paid.
  const prefilled = params.get("to");
  useEffect(() => {
    if (!prefilled || !payees.data || target) return;
    const payee = payees.data.find((candidate) => candidate.accountNumber === prefilled);
    if (payee) {
      setTarget({ accountNumber: payee.accountNumber, name: payee.nickname, own: false });
      setStep("amount");
    } else {
      setTyped((current) => current || prefilled);
    }
  }, [prefilled, payees.data, target]);

  const digits = typed.replace(/[\s-]/g, "");
  useEffect(() => {
    if (digits.length !== 12) {
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
  }, [digits, token]);

  const pay = useMutation({
    mutationFn: () => request<Receipt>("/api/transfers", {
      method: "POST", token, idempotencyKey: key,
      json: { fromAccountId: from?.id, toAccountNumber: target?.accountNumber, amount: amount.trim(), description: note.trim() || null }
    }),
    onSuccess: ({ data, response }) => {
      void refresh();
      setDone({ receipt: data, replayed: response.headers.get("Idempotent-Replayed") === "true" });
      setStep("done");
    },
    // The key stays the same, so retrying this exact payment can never send it twice.
    onError: (reason) => setFailure(reason instanceof ApiError && reason.fields.amount ? reason.fields.amount : errorText(reason))
  });

  function choose(next: Target) {
    setTarget(next);
    setStep("amount");
  }

  function submitAmount(event: FormEvent) {
    event.preventDefault();
    const value = amount.trim();
    if (!AMOUNT.test(value) || Number(value) <= 0) {
      setAmountError("Enter an amount like 2500 or 999.50");
      return;
    }
    if (!from) {
      setAmountError("You need an active account to pay from");
      return;
    }
    if (Number(value) > from.balance) {
      setAmountError(`That is more than the ${money(from.balance)} in ${from.nickname}`);
      return;
    }
    setAmountError(null);
    setFailure(null);
    setStep("review");
  }

  function startOver() {
    setStep("who");
    setTarget(null);
    setTyped("");
    setAmount("");
    setNote("");
    setDone(null);
    setFailure(null);
    setKey(newIdempotencyKey());
  }

  const progress = step === "who" ? 1 : step === "amount" ? 2 : 3;
  const toOthers = target ? !target.own : false;

  return (
    <Page title={ownMode ? "Move money" : "Pay"} lead={ownMode ? "Between your own accounts: instant, and never counted against your daily limit." : "To someone you have paid before, any Florin account number, or yourself."}>
      <div className="pay-layout">
        <Card className="pay-card">
          {step !== "done" ? (
            <div className="steps" aria-hidden="true">
              {[1, 2, 3].map((index) => (
                <div key={index} className="step"><motion.div className="step-fill" initial={false} animate={{ scaleX: index <= progress ? 1 : 0 }} transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }} /></div>
              ))}
            </div>
          ) : null}
          <AnimatePresence mode="wait" initial={false}>
            {step === "who" ? (
              <motion.div key="who" {...slide}>
                {!ownMode ? (
                  <>
                    <Field label="Pay to an account number" mono value={typed} onChange={(event) => setTyped(event.target.value)} inputMode="numeric" placeholder="12-digit account number" autoComplete="off"
                      prefix={<MagnifyingGlass size={16} />} hint="We confirm whose account it is before you pay." />
                    <div className="lookup-slot">
                      {lookup.status === "busy" ? <Skeleton height={58} radius={16} /> : null}
                      {lookup.status === "error" ? <div className="form-error">{lookup.message}</div> : null}
                      {lookup.status === "found" && lookup.result ? (
                        <motion.div className={`lookup-result ${lookup.result.acceptsPayments ? "" : "warn"}`} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                          <Avatar name={lookup.result.holderName} size={42} />
                          <div className="lookup-main">
                            <strong>{lookup.result.holderName} <ShieldCheck size={16} weight="fill" className="verified" /></strong>
                            <span>{lookup.result.ownAccount ? "One of your own accounts" : groupDigits(lookup.result.accountNumber)}{!lookup.result.acceptsPayments ? " · closed" : ""}</span>
                          </div>
                          <Button variant="primary" size="sm" disabled={!lookup.result.acceptsPayments} onClick={() => lookup.result && choose({ accountNumber: lookup.result.accountNumber, name: lookup.result.holderName, own: lookup.result.ownAccount })}>Pay</Button>
                        </motion.div>
                      ) : null}
                    </div>
                    <h3 className="pay-section">People</h3>
                    {payees.data ? (
                      payees.data.length === 0 ? <p className="muted" style={{ fontSize: "0.9rem" }}>No payees yet. <Link to="/payees" className="link-gold">Add one</Link> to pay them in two taps.</p> : (
                        <div className="people-grid">
                          {payees.data.map((payee) => (
                            <button key={payee.id} className="person-tile" disabled={!payee.accountOpen} onClick={() => choose({ accountNumber: payee.accountNumber, name: payee.nickname, own: false })}>
                              <Avatar name={payee.holderName} size={48} />
                              <strong>{payee.nickname}</strong>
                              <span>•••• {payee.accountNumber.slice(-4)}</span>
                            </button>
                          ))}
                        </div>
                      )
                    ) : <Skeleton height={96} radius={16} />}
                  </>
                ) : null}
                <h3 className="pay-section">{ownMode ? "Move money to" : "Self transfer"}</h3>
                <div className="who-grid">
                  {open.map((account) => (
                    <button key={account.id} className="option-card" onClick={() => choose({ accountNumber: account.accountNumber, name: account.nickname, own: true })}>
                      <span className="option-icon"><ArrowsLeftRight size={18} /></span>
                      <div style={{ flex: 1, minWidth: 0 }}><strong>{account.nickname}</strong><span>{groupDigits(account.accountNumber)}</span></div>
                      <Money amount={account.balance} />
                    </button>
                  ))}
                </div>
              </motion.div>
            ) : null}

            {step === "amount" && target ? (
              <motion.form key="amount" {...slide} className="amount-screen" onSubmit={submitAmount} noValidate>
                <div className="payee-hero">
                  <span className="payee-ring"><Avatar name={target.name} size={68} /></span>
                  <strong>{target.own ? `To ${target.name}` : `Paying ${target.name}`}</strong>
                  <span className="mono muted">{groupDigits(target.accountNumber)}</span>
                  {!target.own ? <span className="verified-pill"><ShieldCheck size={14} weight="fill" /> Name confirmed by Florin</span> : null}
                </div>
                <label className={`big-amount ${amountError ? "invalid" : ""}`}>
                  <span className="big-amount-symbol">₹</span>
                  <input value={amount} onChange={(event) => setAmount(event.target.value.replace(/[^\d.]/g, ""))} inputMode="decimal" placeholder="0"
                    aria-label="Amount in rupees" autoComplete="off" autoFocus style={{ width: `${Math.max(1, amount.length || 1) + 0.4}ch` }}
                    aria-invalid={amountError ? true : undefined} aria-describedby={amountError ? "amount-error" : undefined} />
                </label>
                {amountError ? <p className="field-message amount-error" id="amount-error" role="alert">{amountError}</p> : null}
                <div className="quick-amounts">
                  {QUICK.map((value) => <button key={value} type="button" className="chip" onClick={() => setAmount(String(value))}>{money(value, { paise: false })}</button>)}
                </div>
                <input className="note-pill" value={note} onChange={(event) => setNote(event.target.value)} maxLength={140} placeholder="Add a note" aria-label="Note (optional)" />
                <div className="from-chips" role="radiogroup" aria-label="Pay from">
                  {sources.map((account) => (
                    <button key={account.id} type="button" role="radio" aria-checked={from?.id === account.id} className={`from-chip ${from?.id === account.id ? "selected" : ""}`} onClick={() => setFromId(account.id)}>
                      <strong>{account.nickname}</strong>
                      <span>{money(account.balance)}</span>
                    </button>
                  ))}
                </div>
                <div className="form-actions center">
                  <Button type="button" variant="ghost" onClick={() => setStep("who")}>Back</Button>
                  <Button type="submit" variant="primary" size="lg" arrow>Review</Button>
                </div>
              </motion.form>
            ) : null}

            {step === "review" && target && from ? (
              <motion.div key="review" {...slide} className="review">
                <div className="review-amount">
                  <small>You are sending</small>
                  <span className="hero-money hero-display">{money(Number(amount))}</span>
                </div>
                <div className="review-sheet">
                  <div className="review-row"><span>To</span><span>{target.name}<br /><span className="mono muted" style={{ fontSize: "0.8rem" }}>{groupDigits(target.accountNumber)}</span></span></div>
                  <div className="review-row"><span>From</span><span>{from.nickname}<br /><span className="mono muted" style={{ fontSize: "0.8rem" }}>{groupDigits(from.accountNumber)}</span></span></div>
                  {note.trim() ? <div className="review-row"><span>Note</span><span>{note.trim()}</span></div> : null}
                  <div className="review-row"><span>Left in {from.nickname}</span><span>{money(from.balance - Number(amount))}</span></div>
                </div>
                {failure ? <div className="form-error" role="alert">{failure}</div> : null}
                <div className="form-actions center">
                  <Button variant="ghost" onClick={() => setStep("amount")} disabled={pay.isPending}>Back</Button>
                  <Button variant="primary" size="lg" className="shine" loading={pay.isPending} onClick={() => pay.mutate()} arrow>Pay {money(Number(amount), { paise: false })}</Button>
                </div>
              </motion.div>
            ) : null}

            {step === "done" && done ? (
              <motion.div key="done" className="success" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3 }}>
                <div className="success-mark">
                  <Burst />
                  <svg className="success-check" viewBox="0 0 120 120" aria-hidden="true">
                    <motion.circle cx="60" cy="60" r="52" initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }} />
                    <motion.path d="M38 62 L53 77 L84 44" initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 0.45, delay: 0.45, ease: [0.22, 1, 0.36, 1] }} />
                  </svg>
                </div>
                <motion.div className="success-text" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.55, duration: 0.5 }}>
                  <span className="hero-money hero-display">{money(done.receipt.amount)}</span>
                  <p>{done.replayed ? "Already paid. This payment had gone through, so nothing was sent twice." : `Paid to ${done.receipt.to?.name ?? target?.name}`}</p>
                  <span className="muted">{dateTimeLabel(done.receipt.createdAt)}</span>
                  <span className="receipt-ref">{done.receipt.reference}<button className="icon-btn" style={{ width: 28, height: 28 }} aria-label="Copy reference" onClick={() => void navigator.clipboard?.writeText(done.receipt.reference).then(() => toast.show("Reference copied", "success"))}><Copy size={14} /></button></span>
                </motion.div>
                <div className="form-actions center">
                  <Link to={`/receipt/${done.receipt.reference}`} className="btn btn-secondary btn-md"><span>View receipt</span></Link>
                  <Button variant="primary" onClick={startOver}>Done</Button>
                </div>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </Card>

        <Card className="card-tight">
          <div className="side-note">
            <h3>Good to know</h3>
            {limits.data ? (
              <>
                <p>Up to <strong>{money(limits.data.perTransaction, { paise: false })}</strong> per payment to someone else, and <strong>{money(limits.data.remainingToday, { paise: false })}</strong> more today.</p>
                {toOthers && amount && AMOUNT.test(amount) && Number(amount) > limits.data.remainingToday ? <p style={{ color: "var(--bad)" }}>This payment is above what is left of today's limit.</p> : null}
              </>
            ) : <Skeleton height={44} />}
            <p>Moving money between your own accounts has no limit and does not count as spending.</p>
            <p>Every payment carries a one-time key, so a retry after a dropped connection can never send it twice.</p>
          </div>
        </Card>
      </div>
    </Page>
  );
}
