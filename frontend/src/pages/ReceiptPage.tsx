import { Link, useNavigate, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { ArrowDownLeft, ArrowUpRight, ArrowsLeftRight, CaretLeft, Copy } from "@phosphor-icons/react";
import { api, errorText } from "../lib/api";
import { useToken } from "../lib/auth";
import { dateTimeLabel, groupDigits, money } from "../lib/format";
import type { Party, Receipt } from "../lib/types";
import { Button, Card, ErrorNote, Page, Skeleton } from "../components/ui";
import { useToast } from "../components/toast";

function PartyLine({ party }: { party: Party }) {
  return (
    <span>
      {party.name}{party.own ? " (yours)" : ""}
      <br />
      <span className="mono muted" style={{ fontSize: "0.8rem" }}>{party.own ? groupDigits(party.accountNumber) : party.accountNumber}</span>
    </span>
  );
}

export default function ReceiptPage() {
  const { reference = "" } = useParams();
  const token = useToken();
  const toast = useToast();
  const navigate = useNavigate();
  const receipt = useQuery({ queryKey: ["receipt", reference], queryFn: () => api<Receipt>(`/api/transactions/${reference}`, { token }) });
  const data = receipt.data;
  const kind = data?.type;
  const Icon = kind === "DEPOSIT" ? ArrowDownLeft : kind === "WITHDRAWAL" ? ArrowUpRight : ArrowsLeftRight;
  const title = kind === "DEPOSIT" ? "Cash deposit" : kind === "WITHDRAWAL" ? "Cash withdrawal" : data?.from?.own && data?.to?.own ? "Move between accounts" : data?.from?.own ? "Payment sent" : "Payment received";

  return (
    <Page title="Receipt" actions={<Button variant="ghost" icon={<CaretLeft size={16} />} onClick={() => navigate(-1)}>Back</Button>}>
      <div style={{ maxWidth: 560, width: "100%", margin: "0 auto" }}>
        {receipt.isError ? <ErrorNote text={errorText(receipt.error)} retry={() => void receipt.refetch()} /> : null}
        {data ? (
          <Card className="receipt-card">
            <div className="receipt-hero">
              <motion.span className="receipt-check" initial={{ scale: 0.5, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: "spring", stiffness: 240, damping: 18 }}><Icon size={30} weight="bold" /></motion.span>
              <span className="muted">{title}</span>
              <span className="hero-money">{money(data.amount)}</span>
              {data.description ? <p style={{ color: "var(--ink-2)" }}>{data.description}</p> : null}
            </div>
            <div>
              {data.from ? <div className="review-row"><span>From</span><PartyLine party={data.from} /></div> : null}
              {data.to ? <div className="review-row"><span>To</span><PartyLine party={data.to} /></div> : null}
              <div className="review-row"><span>When</span><span>{dateTimeLabel(data.createdAt)}</span></div>
              <div className="review-row"><span>Reference</span><span className="receipt-ref">{data.reference}<button className="icon-btn" style={{ width: 26, height: 26 }} aria-label="Copy reference" onClick={() => void navigator.clipboard?.writeText(data.reference).then(() => toast.show("Reference copied", "success"))}><Copy size={13} /></button></span></div>
            </div>
            <div className="form-actions" style={{ justifyContent: "center", marginTop: 20 }}>
              <Link to="/activity" className="btn btn-secondary btn-md"><span>All activity</span></Link>
              {data.to && !data.to.own && data.from?.own ? <Link to="/pay" className="btn btn-primary btn-md"><span>Pay again</span></Link> : null}
            </div>
          </Card>
        ) : !receipt.isError ? <Skeleton height={360} radius={20} /> : null}
      </div>
    </Page>
  );
}
