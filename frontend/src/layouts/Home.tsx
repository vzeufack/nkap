import React from 'react';
import { Link } from 'react-router-dom';

export default function Home() {
  return (
    <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh', padding: 24 }}>
      <div style={{ textAlign: 'center', maxWidth: 520, width: '100%' }}>
        <div style={{ marginBottom: 20 }}>
          <img
            src="/logo192.png"
            alt="Logo"
            style={{ width: 96, height: 96, objectFit: 'contain' }}
          />
        </div>
        <h1 style={{ margin: 0, fontSize: 32 }}>Welcome to NKAP</h1>
        <p style={{ color: '#555', marginTop: 8 }}>
          Securely manage your account and access app features.
        </p>
        <div style={{ marginTop: 24, display: 'flex', gap: 12, justifyContent: 'center' }}>
          <Link to="/signin">
            <button style={{ padding: '10px 18px' }}>Sign in</button>
          </Link>
          <Link to="/signup">
            <button style={{ padding: '10px 18px' }}>Sign up</button>
          </Link>
        </div>
      </div>
    </div>
  );
}