import { useState } from "react";
import { useAuth } from "./auth/AuthContext";
import { LoginPage } from "./components/LoginPage";
import { ProductWorkspace } from "./components/ProductWorkspace";
import { ConnectionRecovery } from "./components/ConnectionStatus";


export default function App() {
  const { user, status, logout } = useAuth();
  const [signingOut,setSigningOut] = useState(false);
  if(status === "loading") return <div className="loading-screen"><span className="brand-mark">f</span><p>Opening your workspace…</p></div>;
  if(status === "offline") return <ConnectionRecovery />;
  if(!user || status !== "authed") return <LoginPage />;
  return <ProductWorkspace key={user.id} user={user} onLogout={async()=>{setSigningOut(true);try{await logout();}finally{setSigningOut(false);}}} signingOut={signingOut}/>;
}

