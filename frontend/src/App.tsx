import { useState } from 'react'
import SignIn from './layouts/SignIn';
import SignUp from './layouts/SignUp';
import Home from './layouts/Home';
import { BrowserRouter, Routes, Route } from "react-router-dom";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/signin" element={<SignIn disableCustomTheme = {false} />} />
        <Route path="/signup" element={<SignUp disableCustomTheme = {false} />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
