a = int(input())

if a > 10 and a < 5:  # The condition is always false
    pass

if a > 10 and not (a > 10):  # The condition is always false
    pass

if a and not a:  # The condition is always false
    pass

if a or not a:  # The condition is always true
    pass
