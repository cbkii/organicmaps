#include "testing/testregister.hpp"
#include <iostream>
int main()
{
  size_t count=0;
  for (auto test=TestRegister::FirstRegister();test;test=test->m_next)
  {
    try { test->m_fn(); }
    catch (std::exception const & e) { std::cerr<<test->m_testname<<": "<<e.what()<<'\n';return 1; }
    ++count;
  }
  std::cout<<"PASS "<<count<<" retained native tests (headless owner build)\n";
  return count == 0;
}
