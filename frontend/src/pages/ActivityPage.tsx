import { useEffect, useState } from "react";
import { Link } from "react-router";
import { DownloadSimple, MagnifyingGlass, Receipt } from "@phosphor-icons/react";
import { errorText } from "../lib/api";
import { useAccounts, useActivity } from "../lib/queries";
import { daysAgo, isoDate } from "../lib/format";
import type { Direction } from "../lib/types";
import { Button, Card, EmptyState, ErrorNote, Field, Page, Skeleton } from "../components/ui";
import { ActivityList } from "../components/ActivityList";

type Preset = "7" | "30" | "90" | "all" | "custom";

export default function ActivityPage() {
  const accounts = useAccounts();
  const [preset, setPreset] = useState<Preset>("30");
  const [from, setFrom] = useState(() => isoDate(daysAgo(30)));
  const [to, setTo] = useState(() => isoDate(new Date()));
  const [accountId, setAccountId] = useState<number | undefined>(undefined);
  const [direction, setDirection] = useState<Direction | undefined>(undefined);
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);

  // Typing pauses for a third of a second before the search runs.
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => setPage(0), [query, accountId, direction, from, to, preset]);

  function choosePreset(next: Preset) {
    setPreset(next);
    if (next === "7" || next === "30" || next === "90") {
      setFrom(isoDate(daysAgo(Number(next))));
      setTo(isoDate(new Date()));
    }
  }

  const ranged = preset !== "all";
  const activity = useActivity({ accountId, direction, q: query || undefined, from: ranged ? from : undefined, to: ranged ? to : undefined, page, size: 25 });

  return (
    <Page title="Activity" lead="Every movement across your accounts, newest first. Filters apply to everything below."
      actions={accountId ? <Link to={`/accounts/${accountId}`} className="btn btn-secondary btn-md"><DownloadSimple size={16} weight="bold" /><span>Statement for this account</span></Link> : null}>
      <div className="filters">
        <div className="segmented" role="group" aria-label="Date range">
          {([["7", "7 days"], ["30", "30 days"], ["90", "90 days"], ["all", "All time"], ["custom", "Custom"]] as const).map(([value, label]) => (
            <button key={value} className={`seg-btn ${preset === value ? "active" : ""}`} aria-pressed={preset === value} onClick={() => choosePreset(value)}>{label}</button>
          ))}
        </div>
        {preset === "custom" ? (
          <>
            <Field label="From" type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} />
            <Field label="To" type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} />
          </>
        ) : null}
        <select className="select" aria-label="Account" value={accountId ?? ""} onChange={(event) => setAccountId(event.target.value ? Number(event.target.value) : undefined)}>
          <option value="">All accounts</option>
          {accounts.data?.map((account) => <option key={account.id} value={account.id}>{account.nickname}</option>)}
        </select>
        <select className="select" aria-label="Direction" value={direction ?? ""} onChange={(event) => setDirection((event.target.value || undefined) as Direction | undefined)}>
          <option value="">In and out</option>
          <option value="CREDIT">Money in</option>
          <option value="DEBIT">Money out</option>
        </select>
        <Field className="search" label="Search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Note or reference" prefix={<MagnifyingGlass size={16} />} maxLength={60} />
      </div>

      <Card>
        {activity.isError ? <ErrorNote text={errorText(activity.error)} retry={() => void activity.refetch()} /> : null}
        {activity.data ? (
          activity.data.content.length === 0 ? (
            <EmptyState icon={<Receipt size={24} />} title="Nothing matches" text="Try a wider date range or clear the filters." />
          ) : (
            <div style={{ opacity: activity.isFetching ? 0.6 : 1, transition: "opacity 200ms" }}>
              <ActivityList lines={activity.data.content} showBalance={Boolean(accountId)} />
              <div className="pager">
                <span>{activity.data.totalElements} lines · page {page + 1} of {activity.data.totalPages}</span>
                <div className="pager-buttons">
                  <Button size="sm" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Newer</Button>
                  <Button size="sm" disabled={page + 1 >= activity.data.totalPages} onClick={() => setPage((value) => value + 1)}>Older</Button>
                </div>
              </div>
            </div>
          )
        ) : !activity.isError ? <div className="stat-stack"><Skeleton height={54} /><Skeleton height={54} /><Skeleton height={54} /><Skeleton height={54} /></div> : null}
      </Card>
    </Page>
  );
}
