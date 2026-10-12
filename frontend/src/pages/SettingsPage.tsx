import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { DeviceMobile, Laptop, SignOut } from "@phosphor-icons/react";
import { api, ApiError, errorText } from "../lib/api";
import { useAuth, useToken } from "../lib/auth";
import { useTheme, type ThemeChoice } from "../lib/theme";
import { useConfig, useSessions } from "../lib/queries";
import { deviceLabel, money, relativeTime } from "../lib/format";
import type { Customer, Session } from "../lib/types";
import { Button, Card, ErrorNote, Field, Page, Pill, Skeleton } from "../components/ui";
import { useToast } from "../components/toast";

export default function SettingsPage() {
  const token = useToken();
  const { customer, setCustomer } = useAuth();
  const { choice, setChoice } = useTheme();
  const toast = useToast();
  const config = useConfig();
  const sessions = useSessions();
  const [fullName, setFullName] = useState(customer?.fullName ?? "");
  const [nameError, setNameError] = useState<string | null>(null);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const rename = useMutation({
    mutationFn: () => api<Customer>("/api/customers/me", { method: "PATCH", token, json: { fullName } }),
    onSuccess: (updated) => { setCustomer(updated); toast.show("Name updated", "success"); },
    onError: (reason) => setNameError(reason instanceof ApiError && reason.fields.fullName ? reason.fields.fullName : errorText(reason))
  });

  const changePassword = useMutation({
    mutationFn: () => api<void>("/api/auth/password", { method: "POST", token, json: { currentPassword, newPassword } }),
    onSuccess: () => {
      setCurrentPassword("");
      setNewPassword("");
      void sessions.refetch();
      toast.show("Password changed. Other devices were signed out.", "success");
    },
    onError: (reason) => setPasswordError(reason instanceof ApiError && reason.fields.newPassword ? reason.fields.newPassword : errorText(reason))
  });

  const revoke = useMutation({
    mutationFn: (session: Session) => api<void>(`/api/auth/sessions/${session.id}`, { method: "DELETE", token }),
    onSuccess: () => { void sessions.refetch(); toast.show("Device signed out", "success"); },
    onError: (reason) => toast.show(errorText(reason), "error")
  });

  function submitName(event: FormEvent) {
    event.preventDefault();
    setNameError(null);
    rename.mutate();
  }

  function submitPassword(event: FormEvent) {
    event.preventDefault();
    setPasswordError(null);
    changePassword.mutate();
  }

  return (
    <Page title="Settings" lead="Your profile, security and how Florin looks.">
      <div className="settings-grid">
        <Card>
          <div className="card-head"><h2>Profile</h2></div>
          <form className="form-grid" onSubmit={submitName} noValidate>
            <Field label="Full name" value={fullName} onChange={(event) => setFullName(event.target.value)} autoComplete="name" error={nameError} hint="As it appears to people you pay: first name and last initial." />
            <Field label="Email" value={customer?.email ?? ""} readOnly disabled />
            <div className="form-actions"><Button type="submit" variant="primary" loading={rename.isPending} disabled={fullName.trim() === customer?.fullName}>Save</Button></div>
          </form>
        </Card>

        <Card>
          <div className="card-head"><h2>Password</h2></div>
          <form className="form-grid" onSubmit={submitPassword} noValidate>
            <Field label="Current password" type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} autoComplete="current-password" />
            <Field label="New password" type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} autoComplete="new-password" hint="At least 8 characters. Every other device is signed out." error={passwordError} />
            <div className="form-actions"><Button type="submit" variant="primary" loading={changePassword.isPending} disabled={!currentPassword || newPassword.length < 8}>Change password</Button></div>
          </form>
        </Card>

        <Card>
          <div className="card-head"><h2>Signed-in devices</h2></div>
          {sessions.isError ? <ErrorNote text={errorText(sessions.error)} retry={() => void sessions.refetch()} /> : null}
          {sessions.data ? sessions.data.map((session) => (
            <div key={session.id} className="device-row">
              {/iPhone|iPad|Android/.test(session.userAgent ?? "") ? <DeviceMobile size={22} /> : <Laptop size={22} />}
              <div>
                <strong>{deviceLabel(session.userAgent)} {session.current ? <Pill tone="accent">This device</Pill> : null}</strong>
                <span>Signed in {relativeTime(session.createdAt)} · expires {relativeTime(session.expiresAt)}</span>
              </div>
              {!session.current ? <Button size="sm" icon={<SignOut size={15} />} loading={revoke.isPending && revoke.variables?.id === session.id} onClick={() => revoke.mutate(session)}>Sign out</Button> : null}
            </div>
          )) : <div className="stat-stack"><Skeleton height={48} /><Skeleton height={48} /></div>}
        </Card>

        <Card>
          <div className="card-head"><h2>Appearance</h2></div>
          <div className="theme-options" role="group" aria-label="Theme">
            {(["system", "light", "dark"] as ThemeChoice[]).map((option) => (
              <button key={option} className={`theme-option ${choice === option ? "active" : ""}`} aria-pressed={choice === option} onClick={() => setChoice(option)}>
                {option === "system" ? "Match system" : option === "light" ? "Light" : "Dark"}
              </button>
            ))}
          </div>
          {config.data ? (
            <dl className="kv" style={{ marginTop: 24 }}>
              <dt>Branch</dt><dd className="mono">{config.data.branchCode}</dd>
              <dt>Per payment</dt><dd>{money(config.data.perTransactionLimit, { paise: false })}</dd>
              <dt>Daily limit</dt><dd>{money(config.data.dailyOutgoingLimit, { paise: false })}</dd>
              <dt>Bank time zone</dt><dd>{config.data.zone}</dd>
            </dl>
          ) : null}
        </Card>
      </div>
    </Page>
  );
}
