import { lazy, Suspense, type ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router";
import { useAuth } from "./lib/auth";
import { Shell } from "./components/Shell";
import { Splash } from "./components/ui";
import AuthPage from "./pages/AuthPage";

// Each screen is its own chunk; the overview (chart, 3D card) is the heaviest and loads first anyway.
const OverviewPage = lazy(() => import("./pages/OverviewPage"));
const AccountsPage = lazy(() => import("./pages/AccountsPage"));
const AccountPage = lazy(() => import("./pages/AccountPage"));
const PayPage = lazy(() => import("./pages/PayPage"));
const PayeesPage = lazy(() => import("./pages/PayeesPage"));
const ActivityPage = lazy(() => import("./pages/ActivityPage"));
const SettingsPage = lazy(() => import("./pages/SettingsPage"));
const ReceiptPage = lazy(() => import("./pages/ReceiptPage"));

function Protected({ children }: { children: ReactNode }) {
  const { token, ready } = useAuth();
  const location = useLocation();
  if (!ready) return <Splash />;
  if (!token) return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

function Public({ children }: { children: ReactNode }) {
  const { token, ready } = useAuth();
  if (!ready) return <Splash />;
  if (token) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/sign-in" element={<Public><AuthPage mode="sign-in" /></Public>} />
      <Route path="/sign-up" element={<Public><AuthPage mode="sign-up" /></Public>} />
      <Route element={<Protected><Shell /></Protected>}>
        <Route index element={<Lazy><OverviewPage /></Lazy>} />
        <Route path="accounts" element={<Lazy><AccountsPage /></Lazy>} />
        <Route path="accounts/:id" element={<Lazy><AccountPage /></Lazy>} />
        <Route path="pay" element={<Lazy><PayPage /></Lazy>} />
        <Route path="payees" element={<Lazy><PayeesPage /></Lazy>} />
        <Route path="activity" element={<Lazy><ActivityPage /></Lazy>} />
        <Route path="settings" element={<Lazy><SettingsPage /></Lazy>} />
        <Route path="receipt/:reference" element={<Lazy><ReceiptPage /></Lazy>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function Lazy({ children }: { children: ReactNode }) {
  return <Suspense fallback={<div className="page-loading" aria-busy="true" />}>{children}</Suspense>;
}
