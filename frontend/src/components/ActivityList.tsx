import { Link } from "react-router";
import { motion } from "motion/react";
import { ArrowDownLeft, ArrowUpRight } from "@phosphor-icons/react";
import { Money } from "./Money";
import { stagger } from "./ui";
import { dayLabel, timeLabel } from "../lib/format";
import type { StatementLine } from "../lib/types";

export function lineTitle(line: StatementLine): string {
  if (line.description) return line.description;
  if (line.type === "DEPOSIT") return "Cash deposit";
  if (line.type === "WITHDRAWAL") return "Cash withdrawal";
  return line.counterparty ? (line.direction === "DEBIT" ? `To ${line.counterparty.name}` : `From ${line.counterparty.name}`) : "Transfer";
}

export function lineSubtitle(line: StatementLine, showAccount: boolean): string {
  const parts: string[] = [];
  if (line.type === "TRANSFER" && line.counterparty) {
    parts.push(line.direction === "DEBIT" ? `To ${line.counterparty.name}` : `From ${line.counterparty.name}`);
  } else if (line.type === "DEPOSIT") {
    parts.push("Deposit");
  } else if (line.type === "WITHDRAWAL") {
    parts.push("Withdrawal");
  }
  if (showAccount) parts.push(line.account.nickname);
  parts.push(timeLabel(line.createdAt));
  return parts.join(" · ");
}

/** Statement lines grouped by day, newest first, each linking to its receipt. */
export function ActivityList({ lines, showAccount = true, showBalance = false }: { lines: StatementLine[]; showAccount?: boolean; showBalance?: boolean }) {
  const groups: { day: string; lines: StatementLine[] }[] = [];
  for (const line of lines) {
    const day = dayLabel(line.createdAt);
    const group = groups[groups.length - 1];
    if (group && group.day === day) group.lines.push(line);
    else groups.push({ day, lines: [line] });
  }
  return (
    <motion.div className="activity" variants={stagger.container} initial="hidden" animate="show">
      {groups.map((group) => (
        <div key={group.day} className="activity-group">
          <div className="activity-day">{group.day}</div>
          {group.lines.map((line) => {
            const incoming = line.direction === "CREDIT";
            return (
              <motion.div key={line.entryId} variants={stagger.item}>
                <Link to={`/receipt/${line.reference}`} className="activity-row">
                  <span className={`activity-icon ${incoming ? "in" : "out"}`} aria-hidden="true">
                    {incoming ? <ArrowDownLeft size={18} weight="bold" /> : <ArrowUpRight size={18} weight="bold" />}
                  </span>
                  <span className="activity-main">
                    <span className="activity-title">{lineTitle(line)}</span>
                    <span className="activity-sub">{lineSubtitle(line, showAccount)}</span>
                  </span>
                  <span className="activity-amount">
                    <Money amount={incoming ? line.amount : -line.amount} sign="always" tone={incoming ? "in" : "out"} />
                    {showBalance ? <span className="activity-balance">Balance {new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(line.balanceAfter)}</span> : null}
                  </span>
                </Link>
              </motion.div>
            );
          })}
        </div>
      ))}
    </motion.div>
  );
}
