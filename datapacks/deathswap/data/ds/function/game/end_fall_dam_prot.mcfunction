##execute as @a[tag=!no_fall_dam,tag=!keys_inverted] unless score lowGrav Items matches 1.. run attribute @s minecraft:fall_damage_multiplier base reset

gamerule fall_damage true