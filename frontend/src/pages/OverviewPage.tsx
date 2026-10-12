import { lazy, useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router";
import { motion } from "motion/react";
import {
  ArrowDownLeft, ArrowUpRight, ArrowsLeftRight, Bank, DownloadSimple, Eye, EyeSlash, HandCoins, Money as MoneyIcon,
  PaperPlaneTilt, Plus, QrCode, Receipt, TrendDown, TrendUp
} from "@phosphor-icons/react";
import { useAuth } from "../lib/auth";
import { errorText } from "../lib/api";
import { useTheme } from "../lib/theme";
import { useAccounts, useActivity, useConfig, useInsights, useLimits, usePayees } from "../lib/queries";
import { money, monthLabel } from "../lib/format";
import { Avatar, Card, EmptyState, ErrorNote, Skeleton, stagger } from "../components/ui";
import { HeroMoney } from "../components/Money";
import { BalanceChart } from "../components/BalanceChart";
import { AccountCard } from "../components/AccountCard";
import { ActivityList } from "../components/ActivityList";
import { Mark } from "../components/Mark";
import { ReceiveDialog } from "../components/ReceiveDialog";
import { Magnetic, Reveal, Tilt, WordReveal } from "../components/motion";
import { SceneBoundary, use3D, usePageVisible } from "../components/scene3d";

const DottedCoin = lazy(() => import("../components/DottedCoin"));

const RANGES = [30, 90, 180] as const;
const HIDE_KEY = "florin-hide-balance";

function greeting(): string {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
}

function readHidden(): boolean {
  try {
    return localStorage.getItem(HIDE_KEY) === "1";
  } catch {
    return false;
  }
}

function StatTile({ label, value, previous, up, icon }: { label: string; value: number; previous: number; up: "good" | "bad"; icon: ReactNode }) {
  const change = previous > 0 ? ((value - previous) / previous) * 100 : null;
  const direction = change === null || Math.abs(change) < 0.5 ? "flat" : change > 0 ? "up" : "down";
  const tone = direction === "flat" ? "" : (direction === "up") === (up === "good") ? "up" : "down";
  return (
    <div className="stat">
      <span className="stat-label">{icon}{label}</span>
      <span className="stat-value">{money(value, { paise: false })}</span>
      <span className={`stat-delta ${tone}`}>
        {change === null ? "Nothing last month" : direction === "flat" ? "About the same as last month" : `${change > 0 ? "+" : ""}${change.toFixed(0)}% vs last month`}
      </span>
    </div>
  );
}

type Person = { key: string; name: string; accountNumber: string };

export default function OverviewPage() {
  const { customer } = useAuth();
  const { resolved } = useTheme();
  const [days, setDays] = useState<(typeof RANGES)[number]>(30);
  const [hidden, setHidden] = useState(readHidden);
  const [receiving, setReceiving] = useState(false);
  const insights = useInsights(days);
  const accounts = useAccounts();
  const payees = usePayees();
  const recent = useActivity({ size: 8 });
  const limits = useLimits();
  const config = useConfig();
  const webgl = use3D();
  const visible = usePageVisible();

  const firstName = customer?.fullName.split(" ")[0] ?? "";
  const history = insights.data?.history ?? [];
  const total = insights.data?.totalBalance ?? 0;
  const change = total - (history[0]?.balance ?? 0);
  const open = (accounts.data ?? []).filter((account) => account.status !== "CLOSED");
  const primary = open[0];

  // "Pay again": saved payees first, then people you paid recently, like the people row of a UPI app.
  const people = useMemo<Person[]>(() => {
    const list: Person[] = [];
    const seen = new Set<string>();
    for (const payee of payees.data ?? []) {
      if (!payee.accountOpen || seen.has(payee.accountNumber)) continue;
      seen.add(payee.accountNumber);
      list.push({ key: `p${payee.id}`, name: payee.nickname, accountNumber: payee.accountNumber });
    }
    return list.slice(0, 7);
  }, [payees.data]);

  function toggleHidden() {
    setHidden((value) => {
      try {
        localStorage.setItem(HIDE_KEY, value ? "0" : "1");
      } catch {
        /* the choice lasts for this visit */
      }
      return !value;
    });
  }

  const actions = [
    { to: "/pay", icon: PaperPlaneTilt, title: "Pay anyone", text: "By account number" },
    { to: "/pay?mode=own", icon: ArrowsLeftRight, title: "Self transfer", text: "Between your accounts" },
    { to: primary ? `/accounts/${primary.id}?action=deposit` : "/accounts", icon: Plus, title: "Add money", text: "Cash deposit" },
    { to: primary ? `/accounts/${primary.id}?action=withdraw` : "/accounts", icon: MoneyIcon, title: "Withdraw", text: "Cash out" },
    { onClick: () => setReceiving(true), icon: QrCode, title: "Receive", text: "Show your QR" },
    { to: "/activity", icon: DownloadSimple, title: "Statements", text: "CSV for any range" }
  ];

  return (
    <div className="page overview">
      <section className="ov-hero">
        <div className="ov-hero-copy">
          <motion.span className="date-pill" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
            {new Intl.DateTimeFormat("en-IN", { weekday: "long", day: "numeric", month: "long" }).format(new Date())}
          </motion.span>
          <h1 className="ov-title"><WordReveal text={`${greeting()},`} /><WordReveal text={`${firstName}.`} className="gold-text" delay={0.18} /></h1>
          <div className="ov-balance">
            <div className="ov-balance-label">
              <span>Total balance{open.length ? ` across ${open.length} account${open.length === 1 ? "" : "s"}` : ""}</span>
              <button className="eye-btn" onClick={toggleHidden} aria-pressed={hidden} aria-label={hidden ? "Show balance" : "Hide balance"}>
                {hidden ? <Eye size={16} /> : <EyeSlash size={16} />}<span>{hidden ? "Check balance" : "Hide"}</span>
              </button>
            </div>
            {insights.data ? (
              hidden ? <span className="hero-money hero-hidden" aria-label="Balance hidden">₹ ••••••</span> : <HeroMoney amount={total} className="hero-display" />
            ) : <Skeleton width={300} height={64} radius={12} />}
            {insights.data && !hidden ? (
              <span className={`delta-pill ${change > 0 ? "up" : change < 0 ? "down" : ""}`}>
                {change > 0 ? <TrendUp size={15} weight="bold" /> : change < 0 ? <TrendDown size={15} weight="bold" /> : null}
                {change === 0 ? "No change" : `${change > 0 ? "+" : "-"}${money(Math.abs(change), { paise: false })}`} in {days} days
              </span>
            ) : null}
          </div>
          <div className="ov-cta">
            <Magnetic><Link to="/pay" className="btn btn-primary btn-lg shine"><PaperPlaneTilt size={18} weight="fill" /><span>Pay someone</span></Link></Magnetic>
            <Link to="/pay?mode=own" className="btn btn-glass btn-lg"><ArrowsLeftRight size={18} /><span>Move money</span></Link>
            <button className="btn btn-glass btn-lg" onClick={() => setReceiving(true)}><QrCode size={18} /><span>Receive</span></button>
          </div>
        </div>
        <div className="ov-hero-stage" aria-hidden="true">
          <div className="stage-glow" />
          {webgl ? (
            <SceneBoundary fallback={<div className="coin-fallback"><Mark size={260} /></div>}>
              <DottedCoin theme={resolved} active={visible} pulse={Math.round(total)} />
            </SceneBoundary>
          ) : <div className="coin-fallback"><Mark size={240} /></div>}
        </div>
      </section>

      <Reveal className="people-strip">
        <div className="strip-head"><h2>Pay again</h2><Link to="/payees" className="link-quiet">All payees</Link></div>
        <motion.div className="people" variants={stagger.container} initial="hidden" animate="show">
          <motion.div variants={stagger.item}>
            <Link to="/payees" className="person add"><span className="person-ring"><Plus size={22} /></span><span className="person-name">New payee</span></Link>
          </motion.div>
          {people.map((person) => (
            <motion.div key={person.key} variants={stagger.item}>
              <Link to={`/pay?to=${person.accountNumber}`} className="person">
                <span className="person-ring"><Avatar name={person.name} size={52} /></span>
                <span className="person-name">{person.name}</span>
              </Link>
            </motion.div>
          ))}
          {payees.data && people.length === 0 ? <p className="muted people-empty">Save someone as a payee and they appear here for one-tap payments.</p> : null}
        </motion.div>
      </Reveal>

      <div className="bento">
        <Reveal className="span-8">
          <Card className="chart-card">
            <div className="card-head">
              <div><h2>Balance over time</h2><p className="muted card-sub">Closing balance each day, all accounts together</p></div>
              <div className="segmented" role="group" aria-label="Chart range">
                {RANGES.map((range) => (
                  <button key={range} className={`seg-btn ${days === range ? "active" : ""}`} onClick={() => setDays(range)} aria-pressed={days === range}>{range}d</button>
                ))}
              </div>
            </div>
            {insights.isError ? <ErrorNote text={errorText(insights.error)} retry={() => void insights.refetch()} /> : null}
            {insights.data ? (
              <div style={{ opacity: insights.isFetching ? 0.6 : 1, transition: "opacity 200ms" }}>
                <BalanceChart points={history} animateKey={days} />
              </div>
            ) : !insights.isError ? <Skeleton height={240} radius={14} /> : null}
          </Card>
        </Reveal>

        <Reveal className="span-4" delay={0.08}>
          <Card>
            <div className="card-head"><h2>{monthLabel()}</h2><Link to="/activity" className="link-quiet">Details</Link></div>
            {insights.data ? (
              <div className="stat-stack">
                <StatTile label="Money in" value={insights.data.monthIn} previous={insights.data.previousMonthIn} up="good" icon={<ArrowDownLeft size={16} weight="bold" />} />
                <StatTile label="Money out" value={insights.data.monthOut} previous={insights.data.previousMonthOut} up="bad" icon={<ArrowUpRight size={16} weight="bold" />} />
                <p className="muted" style={{ fontSize: "0.8rem" }}>Moves between your own accounts are not counted.</p>
              </div>
            ) : <div className="stat-stack"><Skeleton height={92} radius={14} /><Skeleton height={92} radius={14} /></div>}
          </Card>
        </Reveal>

        <Reveal className="span-5">
          <Card>
            <div className="card-head"><h2>Money moves</h2></div>
            <div className="action-grid">
              {actions.map(({ to, onClick, icon: Icon, title, text }) => {
                const inner = <><span className="action-icon"><Icon size={22} weight="duotone" /></span><strong>{title}</strong><span>{text}</span></>;
                return (
                  <Tilt key={title} className="action-tilt" max={9}>
                    {to ? <Link to={to} className="action-tile">{inner}</Link> : <button className="action-tile" onClick={onClick}>{inner}</button>}
                  </Tilt>
                );
              })}
            </div>
            {limits.data ? (
              <div className="meter">
                <div className="meter-row"><span>Sent today</span><strong>{money(limits.data.usedToday, { paise: false })} of {money(limits.data.dailyOutgoing, { paise: false })}</strong></div>
                <div className="meter-track"><motion.div className="meter-fill" initial={{ scaleX: 0 }} animate={{ scaleX: Math.min(1, limits.data.usedToday / limits.data.dailyOutgoing) }} transition={{ duration: 1.1, ease: [0.22, 1, 0.36, 1] }} /></div>
                <div className="meter-row"><span className="muted">The daily limit resets at midnight.</span></div>
              </div>
            ) : null}
          </Card>
        </Reveal>

        <Reveal className="span-7" delay={0.08}>
          <Card>
            <div className="card-head"><h2>Your accounts</h2><Link to="/accounts" className="link-quiet">Manage</Link></div>
            {accounts.data ? (
              <motion.div className="cards-row" variants={stagger.container} initial="hidden" animate="show">
                {open.map((account) => (
                  <motion.div key={account.id} variants={stagger.item}>
                    <Link to={`/accounts/${account.id}`} className="acard-link" aria-label={`${account.nickname}, ${hidden ? "balance hidden" : money(account.balance)}`}>
                      <AccountCard account={account} holder={customer?.fullName ?? ""} branchCode={config.data?.branchCode} hideBalance={hidden} />
                    </Link>
                  </motion.div>
                ))}
                <motion.div variants={stagger.item}>
                  <Link to="/accounts" className="acard-new"><Bank size={26} /><span>Open another account</span></Link>
                </motion.div>
              </motion.div>
            ) : <Skeleton height={214} radius={18} width={340} />}
          </Card>
        </Reveal>

        <Reveal className="span-12">
          <Card>
            <div className="card-head"><h2>Recent activity</h2><Link to="/activity" className="link-quiet">See everything</Link></div>
            {recent.isError ? <ErrorNote text={errorText(recent.error)} retry={() => void recent.refetch()} /> : null}
            {recent.data ? (
              recent.data.content.length > 0 ? <ActivityList lines={recent.data.content} /> : (
                <EmptyState icon={<Receipt size={24} />} title="Nothing yet" text="Deposits, withdrawals and payments show up here as they happen." action={<Link className="btn btn-primary btn-md" to={primary ? `/accounts/${primary.id}?action=deposit` : "/accounts"}><HandCoins size={16} /><span>Add money</span></Link>} />
              )
            ) : !recent.isError ? <div className="stat-stack"><Skeleton height={54} /><Skeleton height={54} /><Skeleton height={54} /></div> : null}
          </Card>
        </Reveal>
      </div>

      <ReceiveDialog open={receiving} onClose={() => setReceiving(false)} accounts={accounts.data ?? []} holder={customer?.fullName ?? ""} />
    </div>
  );
}
