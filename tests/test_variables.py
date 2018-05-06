a = int(input())

if a > 10 and a < 5:  # The condition is always false
    pass

if a > 10 and not (a > 10):  # The condition is always false
    pass

if a and not a:  # The condition is always false
    pass

if a or not a:  # The condition is always true
    pass

if a > 10 or a < 20:  # The condition is always true
    pass

if a > 1 or a <= 1:  # The condition is always true
    pass
