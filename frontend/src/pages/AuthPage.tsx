import { lazy, useRef, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { motion, useInView } from "motion/react";
import { ArrowDownLeft, Eye, EyeSlash, ShieldCheck } from "@phosphor-icons/react";
import { ApiError, errorText } from "../lib/api";
import { useAuth } from "../lib/auth";
import { useTheme } from "../lib/theme";
import { useConfig } from "../lib/queries";
import { Button, Field } from "../components/ui";
import { Mark } from "../components/Mark";
import { Backdrop } from "../components/Backdrop";
import { Magnetic, trackSpotlight, WordReveal } from "../components/motion";
import { SceneBoundary, use3D, usePageVisible } from "../components/scene3d";

const DottedCoin = lazy(() => import("../components/DottedCoin"));

export default function AuthPage({ mode }: { mode: "sign-in" | "sign-up" }) {
  const { signIn, register } = useAuth();
  const { resolved } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const config = useConfig();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const art = useRef<HTMLDivElement>(null);
  const inView = useInView(art, { amount: 0.2 });
  const visible = usePageVisible();
  const webgl = use3D();
  const signUp = mode === "sign-up";

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFields({});
    try {
      if (signUp) await register(fullName, email, password);
      else await signIn(email, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== "/sign-in" ? from : "/", { replace: true });
    } catch (reason) {
      if (reason instanceof ApiError && Object.keys(reason.fields).length > 0) setFields(reason.fields);
      else setError(errorText(reason));
    } finally {
      setBusy(false);
    }
  }

  const demo = config.data?.demo ?? null;
  const fallback = <div className="coin-fallback auth-coin-fallback"><Mark size={280} /></div>;

  return (
    <div className="auth">
      <Backdrop />
      <div className="auth-art" ref={art}>
        <Link to="/sign-in" className="brand auth-brand"><span className="brand-mark"><Mark size={30} /></span><span className="brand-word">Florin</span></Link>
        <div className="auth-stage" aria-hidden="true">
          <div className="stage-glow" />
          {webgl ? <SceneBoundary fallback={fallback}><DottedCoin theme={resolved} active={inView && visible} zoom={0.92} /></SceneBoundary> : fallback}
          <motion.div className="float-note note-in" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 1.1, duration: 0.7 }}>
            <span className="float-note-icon in"><ArrowDownLeft size={18} weight="bold" /></span>
            <div><strong>+₹28,000.00</strong><small>Invoice paid · Brightwater Co.</small></div>
          </motion.div>
          <motion.div className="float-note note-verify" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 1.35, duration: 0.7 }}>
            <span className="float-note-icon"><ShieldCheck size={18} weight="fill" /></span>
            <div><strong>Payee confirmed</strong><small>Kabir R. · •••• 4374</small></div>
          </motion.div>
        </div>
        <div className="auth-copy">
          <h2><WordReveal text="Your money," delay={0.15} /><WordReveal text="in plain sight." className="gold-text" delay={0.4} /></h2>
          <motion.p initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, delay: 0.75 }}>
            Accounts, payments and statements in one calm place. Every rupee accounted for.
          </motion.p>
        </div>
      </div>

      <div className="auth-panel">
        <motion.form className="auth-form glass-panel" onPointerMove={trackSpotlight} onSubmit={submit}
          initial={{ opacity: 0, y: 18, filter: "blur(8px)" }} animate={{ opacity: 1, y: 0, filter: "blur(0px)" }} transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1], delay: 0.2 }} noValidate>
          <div>
            <h1>{signUp ? "Open your account" : "Welcome back"}</h1>
            <p className="auth-switch">
              {signUp ? <>Already with us? <Link to="/sign-in">Sign in</Link></> : <>New to Florin? <Link to="/sign-up">Create an account</Link></>}
            </p>
          </div>
          {signUp ? <Field label="Full name" value={fullName} onChange={(event) => setFullName(event.target.value)} autoComplete="name" error={fields.fullName} required /> : null}
          <Field label="Email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" inputMode="email" error={fields.email} required />
          <Field
            label="Password"
            type={showPassword ? "text" : "password"}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={signUp ? "new-password" : "current-password"}
            hint={signUp ? "At least 8 characters." : undefined}
            error={fields.password}
            required
            suffix={<button type="button" className="icon-btn" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeSlash size={18} /> : <Eye size={18} />}</button>}
          />
          {error ? <div className="form-error" role="alert">{error}</div> : null}
          <Magnetic strength={0.12}>
            <Button type="submit" variant="primary" size="lg" className="shine wide" loading={busy} arrow>{signUp ? "Create account" : "Sign in"}</Button>
          </Magnetic>
          {!signUp && demo ? (
            <div className="demo-hint">
              <span>Exploring? Use the demo account <code>{demo.email}</code></span>
              <Button type="button" size="sm" onClick={() => { setEmail(demo.email); setPassword(demo.password); }}>Fill in</Button>
            </div>
          ) : null}
          <p className="auth-fineprint">Sessions expire after 7 days. Every sign-in is listed under Settings, where you can end it.</p>
        </motion.form>
      </div>
    </div>
  );
}
