import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { CaretDown, Gear, House, Moon, PaperPlaneTilt, Receipt, SignOut, Sun, UsersThree, Vault } from "@phosphor-icons/react";
import { useAuth } from "../lib/auth";
import { useTheme } from "../lib/theme";
import { Avatar } from "./ui";
import { Mark } from "./Mark";
import { Backdrop } from "./Backdrop";
import { Magnetic } from "./motion";

const NAV = [
  { to: "/", label: "Overview", icon: House, end: true },
  { to: "/accounts", label: "Accounts", icon: Vault },
  { to: "/payees", label: "Payees", icon: UsersThree },
  { to: "/activity", label: "Activity", icon: Receipt }
];

/** The signed-in customer's chip. Sign-out lives inside the menu, never one stray click away. */
function AccountMenu() {
  const { customer, signOut } = useAuth();
  const { resolved, setChoice } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => setOpen(false), [location.pathname]);
  useEffect(() => {
    if (!open) return;
    const onDown = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    const onKey = (event: KeyboardEvent) => { if (event.key === "Escape") setOpen(false); };
    document.addEventListener("pointerdown", onDown);
    document.addEventListener("keydown", onKey);
    root.current?.querySelector<HTMLElement>("[role=menuitem]")?.focus();
    return () => {
      document.removeEventListener("pointerdown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (!customer) return null;
  const firstName = customer.fullName.split(" ")[0];

  return (
    <div className="account-menu" ref={root}>
      <button className={`account-chip ${open ? "open" : ""}`} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        <Avatar name={customer.fullName} size={30} />
        <span className="account-chip-name">{firstName}</span>
        <CaretDown size={14} weight="bold" className="account-caret" />
      </button>
      <AnimatePresence>
        {open ? (
          <motion.div className="menu" role="menu" aria-label="Account"
            initial={{ opacity: 0, y: -8, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ type: "spring", stiffness: 420, damping: 32 }}>
            <div className="menu-head">
              <Avatar name={customer.fullName} size={40} />
              <div><strong>{customer.fullName}</strong><span>{customer.email}</span></div>
            </div>
            <Link role="menuitem" className="menu-item" to="/settings"><Gear size={18} /> Settings and security</Link>
            <button role="menuitem" className="menu-item" onClick={() => setChoice(resolved === "dark" ? "light" : "dark")}>
              {resolved === "dark" ? <Sun size={18} /> : <Moon size={18} />} {resolved === "dark" ? "Light theme" : "Dark theme"}
            </button>
            <div className="menu-divider" role="separator" />
            <button role="menuitem" className="menu-item danger" onClick={() => void signOut().then(() => navigate("/sign-in"))}><SignOut size={18} /> Sign out</button>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}

export function Shell() {
  const { reconnecting } = useAuth();
  const location = useLocation();
  const onOverview = location.pathname === "/";

  return (
    <div className="shell">
      <Backdrop ribbons={onOverview} />
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand" aria-label="Florin overview">
            <span className="brand-mark"><Mark size={30} /></span>
            <span className="brand-word">Florin</span>
          </Link>
          <nav className="topnav" aria-label="Main">
            {NAV.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} className={({ isActive }) => `topnav-link ${isActive ? "active" : ""}`}>
                {({ isActive }) => (
                  <>
                    {isActive ? <motion.span layoutId="topnav-pill" className="topnav-pill" transition={{ type: "spring", stiffness: 420, damping: 34 }} /> : null}
                    <Icon size={18} weight={isActive ? "fill" : "regular"} />
                    <span>{label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>
          <div className="topbar-actions">
            <Magnetic>
              <Link to="/pay" className="btn btn-primary btn-sm shine topbar-pay"><PaperPlaneTilt size={16} weight="fill" /><span>Pay</span></Link>
            </Magnetic>
            <AccountMenu />
          </div>
        </div>
      </header>

      <main className="content">
        {reconnecting ? <div className="banner" role="status">Reconnecting to Florin. Your session is safe.</div> : null}
        <Outlet />
      </main>

      <nav className="island" aria-label="Main">
        {NAV.slice(0, 2).map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => `island-tab ${isActive ? "active" : ""}`}>
            {({ isActive }) => <><Icon size={22} weight={isActive ? "fill" : "regular"} /><span>{label}</span></>}
          </NavLink>
        ))}
        <NavLink to="/pay" className={({ isActive }) => `island-pay ${isActive ? "active" : ""}`} aria-label="Pay">
          <PaperPlaneTilt size={24} weight="fill" />
        </NavLink>
        {NAV.slice(2).map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} className={({ isActive }) => `island-tab ${isActive ? "active" : ""}`}>
            {({ isActive }) => <><Icon size={22} weight={isActive ? "fill" : "regular"} /><span>{label}</span></>}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
