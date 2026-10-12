import { useEffect, useState } from "react";
import { Copy, ShareNetwork } from "@phosphor-icons/react";
import type { Account } from "../lib/types";
import { groupDigits, money } from "../lib/format";
import { Button, Dialog } from "./ui";
import { DotQR } from "./DotQR";
import { useToast } from "./toast";

/** Show a code someone can scan to pay you: it opens Florin's pay screen with this account filled in. */
export function ReceiveDialog({ open, onClose, accounts, holder }: { open: boolean; onClose: () => void; accounts: Account[]; holder: string }) {
  const toast = useToast();
  const usable = accounts.filter((account) => account.status !== "CLOSED");
  const [selected, setSelected] = useState<number | null>(null);
  useEffect(() => {
    if (open && selected === null && usable.length > 0) setSelected(usable[0].id);
  }, [open, selected, usable]);
  const account = usable.find((candidate) => candidate.id === selected) ?? usable[0];
  if (!account) return null;

  const link = `${window.location.origin}${import.meta.env.BASE_URL}pay?to=${account.accountNumber}`;
  const shareText = `Pay ${holder} on Florin: account ${groupDigits(account.accountNumber)}. ${link}`;

  async function share() {
    try {
      if (navigator.share) await navigator.share({ title: "Pay me on Florin", text: shareText, url: link });
      else {
        await navigator.clipboard.writeText(shareText);
        toast.show("Payment details copied", "success");
      }
    } catch {
      /* the share sheet was dismissed */
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title="Receive money" width={420}>
      <div className="receive">
        {usable.length > 1 ? (
          <div className="segmented receive-accounts" role="group" aria-label="Account to receive into">
            {usable.map((candidate) => (
              <button key={candidate.id} className={`seg-btn ${candidate.id === account.id ? "active" : ""}`} aria-pressed={candidate.id === account.id} onClick={() => setSelected(candidate.id)}>
                {candidate.nickname}
              </button>
            ))}
          </div>
        ) : null}
        <div className="receive-qr">
          <DotQR value={link} size={232} label={`QR code to pay ${account.nickname}`} />
        </div>
        <strong className="receive-name">{holder}</strong>
        <span className="receive-number mono">{groupDigits(account.accountNumber)}</span>
        <span className="muted receive-hint">{account.nickname} · balance {money(account.balance)}</span>
        <p className="muted receive-note">Anyone with Florin can scan this to pay you. It only shares your name and account number.</p>
        <div className="form-actions" style={{ justifyContent: "center" }}>
          <Button icon={<Copy size={16} />} onClick={() => void navigator.clipboard?.writeText(account.accountNumber).then(() => toast.show("Account number copied", "success"))}>Copy number</Button>
          <Button variant="primary" icon={<ShareNetwork size={16} weight="bold" />} onClick={() => void share()}>Share</Button>
        </div>
      </div>
    </Dialog>
  );
}
